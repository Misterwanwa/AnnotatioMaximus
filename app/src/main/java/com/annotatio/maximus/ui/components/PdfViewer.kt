package com.annotatio.maximus.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.annotatio.maximus.model.PageTextContent
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle

data class PdfPageInfo(
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    val pageTextContent: PageTextContent? = null
)

@Composable
fun PdfViewer(
    pdfUri: Uri?,
    modifier: Modifier = Modifier,
    onPageChanged: (page: Int, pageCount: Int) -> Unit = { _, _ -> },
    onPageInfoUpdated: (PdfPageInfo) -> Unit = {}
) {
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PDFView(context, null).also { view ->
                pdfView = view
            }
        }
    )

    LaunchedEffect(pdfUri) {
        val view = pdfView ?: return@LaunchedEffect
        if (pdfUri == null || pdfUri == loadedUri) return@LaunchedEffect
        loadedUri = pdfUri

        val inputStream = view.context.contentResolver.openInputStream(pdfUri)
            ?: return@LaunchedEffect

        view.fromStream(inputStream)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .scrollHandle(DefaultScrollHandle(view.context))
            .spacing(8)
            .onPageChange { page: Int, pageCount: Int ->
                onPageChanged(page, pageCount)
            }
            .onRender { _: Int ->
                val zoom = view.zoom
                val currentPage = view.currentPage
                val pageSize = view.getPageSize(currentPage)
                val pageOffset = getPageOffset(view, currentPage)
                onPageInfoUpdated(
                    PdfPageInfo(
                        pageWidth = pageSize.width * zoom,
                        pageHeight = pageSize.height * zoom,
                        offsetX = view.currentXOffset + pageOffset.first,
                        offsetY = view.currentYOffset + pageOffset.second,
                        zoom = zoom
                    )
                )
            }
            .onPageScroll { _, _ ->
                val zoom = view.zoom
                val currentPage = view.currentPage
                val pageSize = view.getPageSize(currentPage)
                val pageOffset = getPageOffset(view, currentPage)
                onPageInfoUpdated(
                    PdfPageInfo(
                        pageWidth = pageSize.width * zoom,
                        pageHeight = pageSize.height * zoom,
                        offsetX = view.currentXOffset + pageOffset.first,
                        offsetY = view.currentYOffset + pageOffset.second,
                        zoom = zoom
                    )
                )
            }
            .load()
    }
}

private object PdfViewReflectionCache {
    private var pdfFileField: java.lang.reflect.Field? = null
    private var cachedPdfFileClass: Class<*>? = null
    private var getPageOffsetMethod: java.lang.reflect.Method? = null
    private var getSecondaryPageOffsetMethod: java.lang.reflect.Method? = null
    private var reflectionInitialized = false

    fun getPageOffset(view: PDFView, pageIndex: Int): Pair<Float, Float> {
        try {
            if (!reflectionInitialized) {
                try {
                    pdfFileField = PDFView::class.java.getDeclaredField("pdfFile").apply {
                        isAccessible = true
                    }
                } catch (_: Exception) {}
                reflectionInitialized = true
            }

            val pdfFile = pdfFileField?.get(view) ?: return Pair(0f, 0f)
            val pdfFileClass = pdfFile.javaClass

            if (cachedPdfFileClass != pdfFileClass) {
                cachedPdfFileClass = pdfFileClass
                getPageOffsetMethod = pdfFileClass.getMethod(
                    "getPageOffset",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
                getSecondaryPageOffsetMethod = pdfFileClass.getMethod(
                    "getSecondaryPageOffset",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
            }

            val zoom = view.zoom
            val primaryOffset = getPageOffsetMethod?.invoke(pdfFile, pageIndex, zoom) as? Float ?: 0f
            val secondaryOffset = getSecondaryPageOffsetMethod?.invoke(pdfFile, pageIndex, zoom) as? Float ?: 0f

            val isHorizontal = !view.isSwipeVertical

            return if (isHorizontal) {
                Pair(primaryOffset, secondaryOffset)
            } else {
                Pair(secondaryOffset, primaryOffset)
            }
        } catch (_: Exception) {
            return Pair(0f, 0f)
        }
    }
}

private fun getPageOffset(view: PDFView, pageIndex: Int): Pair<Float, Float> {
    return PdfViewReflectionCache.getPageOffset(view, pageIndex)
}
