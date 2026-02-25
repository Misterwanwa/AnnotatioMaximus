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
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle

data class PdfPageInfo(
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f
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
            .onPageChange { page, pageCount ->
                onPageChanged(page, pageCount)
            }
            .onRender { _ ->
                val zoom = view.zoom
                val pageSize = view.getPageSize(view.currentPage)
                onPageInfoUpdated(
                    PdfPageInfo(
                        pageWidth = pageSize.width * zoom,
                        pageHeight = pageSize.height * zoom,
                        offsetX = view.currentXOffset,
                        offsetY = view.currentYOffset,
                        zoom = zoom
                    )
                )
            }
            .onPageScroll { page, _ ->
                val zoom = view.zoom
                val pageSize = view.getPageSize(page)
                onPageInfoUpdated(
                    PdfPageInfo(
                        pageWidth = pageSize.width * zoom,
                        pageHeight = pageSize.height * zoom,
                        offsetX = view.currentXOffset,
                        offsetY = view.currentYOffset,
                        zoom = zoom
                    )
                )
            }
            .load()
    }
}
