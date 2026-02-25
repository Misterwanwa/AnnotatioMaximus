package com.annotatio.maximus.util

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.annotatio.maximus.model.Annotation
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState

object PdfAnnotationSaver {

    fun save(
        context: Context,
        sourceUri: Uri,
        outputUri: Uri,
        annotations: Map<Int, List<Annotation>>
    ) {
        PDFBoxResourceLoader.init(context)

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            try {
                for ((pageIndex, pageAnnotations) in annotations) {
                    if (pageIndex >= document.numberOfPages) continue
                    val page = document.getPage(pageIndex)
                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height

                    PDPageContentStream(
                        document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { contentStream ->
                        for (annotation in pageAnnotations) {
                            when (annotation) {
                                is Annotation.Stroke -> drawStroke(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.HighlightRect -> drawHighlight(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.TextNote -> drawTextNote(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                            }
                        }
                    }
                }

                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    document.save(outputStream)
                    outputStream.flush()
                } ?: throw IllegalStateException("Konnte Ausgabedatei nicht öffnen")
            } finally {
                document.close()
            }
        } ?: throw IllegalStateException("Konnte PDF nicht lesen")
    }

    private fun drawStroke(
        cs: PDPageContentStream,
        stroke: Annotation.Stroke,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val path = stroke.path
        if (path.points.size < 2) return

        val rgb = colorToRgbFloats(path.color)

        if (path.isHighlighter) {
            val gs = PDExtendedGraphicsState()
            gs.strokingAlphaConstant = 0.35f
            cs.setGraphicsStateParameters(gs)
        }

        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(path.strokeWidth * 0.5f)
        cs.setLineCapStyle(1) // Round cap

        val first = path.points.first()
        cs.moveTo(
            first.x * pageWidth,
            pageHeight - (first.y * pageHeight)
        )

        for (i in 1 until path.points.size) {
            val pt = path.points[i]
            cs.lineTo(
                pt.x * pageWidth,
                pageHeight - (pt.y * pageHeight)
            )
        }
        cs.stroke()

        // Reset transparency
        if (path.isHighlighter) {
            val gs = PDExtendedGraphicsState()
            gs.strokingAlphaConstant = 1f
            cs.setGraphicsStateParameters(gs)
        }
    }

    private fun drawHighlight(
        cs: PDPageContentStream,
        highlight: Annotation.HighlightRect,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(highlight.color)

        val gs = PDExtendedGraphicsState()
        gs.nonStrokingAlphaConstant = 0.35f
        cs.setGraphicsStateParameters(gs)

        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])

        val x = highlight.x * pageWidth
        val y = pageHeight - ((highlight.y + highlight.height) * pageHeight)
        val w = highlight.width * pageWidth
        val h = highlight.height * pageHeight

        cs.addRect(x, y, w, h)
        cs.fill()

        // Reset transparency
        val gsReset = PDExtendedGraphicsState()
        gsReset.nonStrokingAlphaConstant = 1f
        cs.setGraphicsStateParameters(gsReset)
    }

    private fun drawTextNote(
        cs: PDPageContentStream,
        note: Annotation.TextNote,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(note.color)
        val x = note.x * pageWidth
        val y = pageHeight - (note.y * pageHeight)

        // Draw marker circle
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
        drawCircle(cs, x, y, 6f)

        // Draw text label next to marker
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 8f)
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.newLineAtOffset(x + 10f, y - 3f)
        cs.showText(note.text.take(80))
        cs.endText()
    }

    private fun drawCircle(
        cs: PDPageContentStream,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        // Approximate circle with 4 cubic Bezier curves (quarter circles)
        val k = 0.5523f * r
        cs.moveTo(cx, cy + r)
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy)
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r)
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy)
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r)
        cs.fill()
    }

    private fun colorToRgbFloats(color: Color): FloatArray {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}
