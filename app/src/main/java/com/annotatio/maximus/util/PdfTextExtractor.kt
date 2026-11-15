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
                    appendLinesFromPositions(text, textPositions, pageLines.getOrPut(currentPage) { mutableListOf() })
                }

                override fun endDocument(document: PDDocument) {
                    super.endDocument(document)

                    // Merge fragments on the same baseline, then normalize per page
                    pageLines.forEach { (pageNum, rawLines) ->
                        val merged = mergeSameBaselineLines(rawLines)
                        val page = document.pages[pageNum]
                        val mediaBox = page.mediaBox
                        val normalizedLines = normalizeTextLines(merged, mediaBox?.width, mediaBox?.height)

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
                    appendLinesFromPositions(text, textPositions, lines)
                }
            }

            stripper.getText(document)

            val mergedLines = mergeSameBaselineLines(stripper.lines)
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageWidth = mediaBox.width
            val pageHeight = mediaBox.height
            val normalizedLines = normalizeTextLines(mergedLines, pageWidth, pageHeight)

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

    private fun appendLinesFromPositions(
        text: String,
        textPositions: List<TextPosition>,
        target: MutableList<TextLine>
    ) {
        if (text.isBlank() || textPositions.isEmpty()) return
        val positionsByY = textPositions.groupBy { pos ->
            kotlin.math.round(pos.y * 2f) / 2f
        }
        positionsByY.forEach { (_, positions) ->
            if (positions.isNotEmpty()) {
                val sorted = positions.sortedBy { it.x }
                val minX = sorted.minOf { it.x }
                val maxX = sorted.maxOf { it.x + it.width }
                val y = sorted.first().y
                val height = sorted.maxOf { it.height }
                val lineText = sorted.joinToString("") { it.unicode }
                target.add(
                    TextLine(
                        text = lineText,
                        x = minX,
                        y = y,
                        width = (maxX - minX).coerceAtLeast(0f),
                        height = height
                    )
                )
            }
        }
    }

    private fun mergeSameBaselineLines(rawLines: List<TextLine>): List<TextLine> {
        if (rawLines.isEmpty()) return emptyList()
        val sortedByY = rawLines.sortedBy { it.y }
        val buckets = mutableListOf<MutableList<TextLine>>()

        for (line in sortedByY) {
            val currentBucket = buckets.lastOrNull()
            val tolerance = (line.height.takeIf { it > 0f } ?: 10f) * 0.35f
            if (currentBucket != null && kotlin.math.abs(line.y - currentBucket.first().y) <= tolerance.coerceAtLeast(2f)) {
                currentBucket.add(line)
            } else {
                buckets.add(mutableListOf(line))
            }
        }

        return buckets.map { group ->
            val ordered = group.sortedBy { it.x }
            val minX = ordered.minOf { it.x }
            val maxX = ordered.maxOf { it.x + it.width }
            val avgY = ordered.map { it.y }.average().toFloat()
            val maxH = ordered.maxOf { it.height }
            val sb = StringBuilder()
            var prevRight = Float.NaN
            for (frag in ordered) {
                val text = frag.text
                if (sb.isNotEmpty() && !prevRight.isNaN()) {
                    val gap = frag.x - prevRight
                    val spaceThreshold = (frag.height.takeIf { it > 0f } ?: 8f) * 0.2f
                    if (gap > spaceThreshold && !sb.endsWith(" ") && !text.startsWith(" ")) {
                        sb.append(' ')
                    }
                }
                sb.append(text)
                prevRight = frag.x + frag.width
            }
            TextLine(
                text = sb.toString(),
                x = minX,
                y = avgY,
                width = (maxX - minX).coerceAtLeast(0f),
                height = maxH
            )
        }
    }

    private fun normalizeTextLines(
        lines: List<TextLine>,
        pageWidth: Float?,
        pageHeight: Float?
    ): List<TextLine> {
        if (pageWidth == null || pageHeight == null || pageWidth <= 0f || pageHeight <= 0f) {
            return lines
        }
        return lines.map { line ->
            line.copy(
                x = (line.x / pageWidth).coerceIn(0f, 1f),
                y = (line.y / pageHeight).coerceIn(0f, 1f),
                width = (line.width / pageWidth).coerceIn(0f, 1f),
                height = (line.height / pageHeight).coerceIn(0f, 1f)
            )
        }
    }
}
