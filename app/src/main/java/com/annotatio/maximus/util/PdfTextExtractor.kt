package com.annotatio.maximus.util

import android.content.Context
import android.net.Uri
import com.annotatio.maximus.model.PageTextContent
import com.annotatio.maximus.model.TextLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Extracts text with positions from PDF pages using PDFBox.
 */
object PdfTextExtractor {
    
    /**
     * Initialize PDFBox (call once in Application.onCreate or before first use)
     */
    fun initialize(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }
    
    /**
     * Extract text content from all pages of a PDF.
     */
    suspend fun extractTextFromPdf(
        context: Context,
        uri: Uri
    ): Map<Int, PageTextContent> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Int, PageTextContent>()
        
        var inputStream: InputStream? = null
        var document: PDDocument? = null
        
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            document = PDDocument.load(inputStream)
            
            val stripper = object : PDFTextStripper() {
                private var currentPage = 0
                private val pageLines = mutableMapOf<Int, MutableList<TextLine>>()
                
                init {
                    sortByPosition = true
                    addMoreFormatting = true
                }
                
                override fun startPage(page: PDPage) {
                    super.startPage(page)
                    // PDFBox 2.x uses getCurrentPageNo() which is 1-based
                    currentPage = currentPageNo - 1
                    pageLines.getOrPut(currentPage) { mutableListOf() }
                }
                
                override fun writeString(
                    text: String,
                    textPositions: MutableList<TextPosition>
                ) {
                    if (text.isBlank() || textPositions.isEmpty()) return
                    
                    // Group text positions by their Y coordinate (same line detection)
                    val positionsByY = textPositions.groupBy { pos ->
                        // Round Y to nearest 0.5 to handle slight variations
                        kotlin.math.round(pos.y * 2) / 2
                    }
                    
                    positionsByY.forEach { (_, positions) ->
                        if (positions.isNotEmpty()) {
                            val minX = positions.minOf { it.x }
                            val maxX = positions.maxOf { it.x + it.width }
                            val y = positions.first().y
                            val height = positions.first().height
                            val lineText = positions.joinToString("") { it.unicode }
                            
                            val textLine = TextLine(
                                text = lineText,
                                x = minX,
                                y = y,  // PDFBox Y is baseline
                                width = maxX - minX,
                                height = height
                            )
                            
                            pageLines.getOrPut(currentPage) { mutableListOf() }.add(textLine)
                        }
                    }
                }
                
                override fun endDocument(document: PDDocument) {
                    super.endDocument(document)
                    
                    // Convert to PageTextContent objects
                    pageLines.forEach { (pageNum, lines) ->
                        // Sort lines by Y position (top to bottom)
                        val sortedLines = lines.sortedBy { it.y }
                        
                        // Normalize coordinates if page size is available
                        val page = document.pages[pageNum]
                        val mediaBox = page.mediaBox
                        
                        val normalizedLines = if (mediaBox != null) {
                            val pageWidth = mediaBox.width
                            val pageHeight = mediaBox.height
                            
                            sortedLines.map { line ->
                                line.copy(
                                    x = (line.x / pageWidth).coerceIn(0f, 1f),
                                    y = (line.y / pageHeight).coerceIn(0f, 1f),
                                    width = (line.width / pageWidth).coerceIn(0f, 1f),
                                    height = (line.height / pageHeight).coerceIn(0f, 1f)
                                )
                            }
                        } else {
                            sortedLines
                        }
                        
                        result[pageNum] = PageTextContent(
                            pageIndex = pageNum,
                            textLines = normalizedLines,
                            pageWidth = mediaBox?.width ?: 0f,
                            pageHeight = mediaBox?.height ?: 0f
                        )
                    }
                }
            }
            
            stripper.getText(document)
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
        
        result
    }
    
    /**
     * Extract text from a specific page only.
     */
    suspend fun extractTextFromPage(
        context: Context,
        uri: Uri,
        pageIndex: Int
    ): PageTextContent? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var document: PDDocument? = null
        
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            document = PDDocument.load(inputStream)
            
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                return@withContext null
            }
            
            val stripper = object : PDFTextStripper() {
                val lines = mutableListOf<TextLine>()
                
                init {
                    sortByPosition = true
                    startPage = pageIndex + 1  // PDFBox uses 1-based indexing
                    endPage = pageIndex + 1
                }
                
                override fun writeString(
                    text: String,
                    textPositions: MutableList<TextPosition>
                ) {
                    if (text.isBlank() || textPositions.isEmpty()) return
                    
                    // Group by Y coordinate (same line)
                    val positionsByY = textPositions.groupBy { pos ->
                        kotlin.math.round(pos.y * 2) / 2
                    }
                    
                    positionsByY.forEach { (_, positions) ->
                        if (positions.isNotEmpty()) {
                            val minX = positions.minOf { it.x }
                            val maxX = positions.maxOf { it.x + it.width }
                            val y = positions.first().y
                            val height = positions.first().height
                            val lineText = positions.joinToString("") { it.unicode }
                            
                            lines.add(TextLine(
                                text = lineText,
                                x = minX,
                                y = y,
                                width = maxX - minX,
                                height = height
                            ))
                        }
                    }
                }
            }
            
            stripper.getText(document)
            
            // Sort lines by Y position
            val sortedLines = stripper.lines.sortedBy { it.y }
            
            // Get page size for normalization
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageWidth = mediaBox.width
            val pageHeight = mediaBox.height
            
            // Normalize coordinates
            val normalizedLines = sortedLines.map { line ->
                line.copy(
                    x = (line.x / pageWidth).coerceIn(0f, 1f),
                    y = (line.y / pageHeight).coerceIn(0f, 1f),
                    width = (line.width / pageWidth).coerceIn(0f, 1f),
                    height = (line.height / pageHeight).coerceIn(0f, 1f)
                )
            }
            
            PageTextContent(
                pageIndex = pageIndex,
                textLines = normalizedLines,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
    }
}
