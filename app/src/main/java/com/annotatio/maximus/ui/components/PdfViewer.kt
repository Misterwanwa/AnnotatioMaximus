package com.annotatio.maximus.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val pdfPointWidth: Float = 595f,
    val pdfPointHeight: Float = 842f,
    val pageTextContent: PageTextContent? = null
)

@Composable
fun PdfViewer(
    pdfUri: Uri?,
    modifier: Modifier = Modifier,
    currentPage: Int = 0,
    onPageChanged: (page: Int, pageCount: Int) -> Unit = { _, _ -> },
    onPageInfoUpdated: (PdfPageInfo) -> Unit = {},
    onPageTap: ((Offset) -> Boolean)? = null
) {
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    var loadedUri by remember { mutableStateOf<Uri?>(null) }
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentOnPageInfoUpdated by rememberUpdatedState(onPageInfoUpdated)
    val currentOnPageTap by rememberUpdatedState(onPageTap)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PDFView(context, null).also { view ->
                pdfView = view
            }
        }
    )

    LaunchedEffect(currentPage, pdfView) {
        val view = pdfView ?: return@LaunchedEffect
        if (view.pageCount > 0 && currentPage in 0 until view.pageCount && view.currentPage != currentPage) {
            view.jumpTo(currentPage, true)
        }
    }

    LaunchedEffect(pdfUri, pdfView) {
        val view = pdfView ?: return@LaunchedEffect
        if (pdfUri == null || pdfUri == loadedUri) return@LaunchedEffect
        loadedUri = pdfUri

        val inputStream = view.context.contentResolver.openInputStream(pdfUri)
            ?: return@LaunchedEffect

        view.fromStream(inputStream)
            .defaultPage(currentPage.coerceAtLeast(0))
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .scrollHandle(DefaultScrollHandle(view.context))
            .spacing(8)
            .onPageChange { page: Int, pageCount: Int ->
                currentOnPageChanged(page, pageCount)
                emitPageInfo(view, page, currentOnPageInfoUpdated)
            }
            .onRender { _: Int ->
                emitPageInfo(view, view.currentPage, currentOnPageInfoUpdated)
            }
            .onPageScroll { _, _ ->
                emitPageInfo(view, view.currentPage, currentOnPageInfoUpdated)
            }
            .onTap { event ->
                if (event != null) {
                    currentOnPageTap?.invoke(Offset(event.x, event.y)) ?: false
                } else {
                    false
                }
            }
            .load()
    }
}

private fun emitPageInfo(
    view: PDFView,
    pageIndex: Int,
    onPageInfoUpdated: (PdfPageInfo) -> Unit
) {
    val pageCount = view.pageCount
    if (pageCount <= 0 || pageIndex !in 0 until pageCount) return
    val zoom = view.zoom
    val pageSize = view.getPageSize(pageIndex)
    if (pageSize.width <= 0f || pageSize.height <= 0f) return
    val pageOffset = getPageOffset(view, pageIndex)
    onPageInfoUpdated(
        PdfPageInfo(
            pageWidth = pageSize.width * zoom,
            pageHeight = pageSize.height * zoom,
            offsetX = view.currentXOffset + pageOffset.first,
            offsetY = view.currentYOffset + pageOffset.second,
            zoom = zoom,
            pdfPointWidth = pageSize.width,
            pdfPointHeight = pageSize.height
        )
    )
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
