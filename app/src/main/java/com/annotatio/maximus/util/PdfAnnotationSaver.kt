package com.annotatio.maximus.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.ShapeType
import com.annotatio.maximus.model.SmartGraphicType
import com.annotatio.maximus.model.TextBoxFont
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink

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
                                is Annotation.Shape -> drawShape(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Signature -> drawSignature(
                                    document, contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Underline -> drawUnderline(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Strikethrough -> drawStrikethrough(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Table -> drawTable(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Comment -> drawComment(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Image -> drawImage(
                                    context, document, contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.Link -> drawLink(
                                    page, contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.SmartGraphic -> drawSmartGraphic(
                                    contentStream, annotation, pageWidth, pageHeight
                                )
                                is Annotation.TextBox -> drawTextBox(
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

        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
        drawCircle(cs, x, y, 6f)

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 8f)
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.newLineAtOffset(x + 10f, y - 3f)
        cs.showText(note.text.take(80).replace("\n", " "))
        cs.endText()
    }

    private fun drawShape(
        cs: PDPageContentStream,
        shape: Annotation.Shape,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(shape.color)
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(shape.strokeWidth * 0.5f)

        val x = shape.x * pageWidth
        val y = pageHeight - ((shape.y + shape.height) * pageHeight)
        val w = shape.width * pageWidth
        val h = shape.height * pageHeight

        when (shape.type) {
            ShapeType.RECTANGLE, ShapeType.SQUARE -> {
                cs.addRect(x, y, w, h)
                cs.stroke()
            }
            ShapeType.CIRCLE -> {
                val cx = x + w / 2f
                val cy = y + h / 2f
                val r = minOf(w, h) / 2f
                drawCircleOutline(cs, cx, cy, r)
            }
            ShapeType.TRIANGLE -> {
                cs.moveTo(x + w / 2f, y + h)
                cs.lineTo(x, y)
                cs.lineTo(x + w, y)
                cs.closePath()
                cs.stroke()
            }
        }
    }

    private fun drawSignature(
        document: PDDocument,
        cs: PDPageContentStream,
        sig: Annotation.Signature,
        pageWidth: Float,
        pageHeight: Float
    ) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(sig.bitmapData, 0, sig.bitmapData.size) ?: return
            val pdImage = LosslessFactory.createFromImage(document, bitmap)
            val x = sig.x * pageWidth
            val y = pageHeight - ((sig.y + sig.height) * pageHeight)
            val w = sig.width * pageWidth
            val h = sig.height * pageHeight
            cs.drawImage(pdImage, x, y, w, h)
        } catch (_: Exception) {}
    }

    private fun drawImage(
        context: Context,
        document: PDDocument,
        cs: PDPageContentStream,
        image: Annotation.Image,
        pageWidth: Float,
        pageHeight: Float
    ) {
        try {
            val uri = Uri.parse(image.uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream) ?: return
                val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.85f)
                val x = image.x * pageWidth
                val y = pageHeight - ((image.y + image.height) * pageHeight)
                val w = image.width * pageWidth
                val h = image.height * pageHeight
                cs.drawImage(pdImage, x, y, w, h)
            }
        } catch (_: Exception) {}
    }

    private fun drawTable(
        cs: PDPageContentStream,
        table: Annotation.Table,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(table.color)
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1f)

        val x = table.x * pageWidth
        val y = pageHeight - ((table.y + table.height) * pageHeight)
        val w = table.width * pageWidth
        val h = table.height * pageHeight

        // Outer border
        cs.addRect(x, y, w, h)
        cs.stroke()

        if (table.rows <= 0 || table.cols <= 0) return
        val cellW = w / table.cols
        val cellH = h / table.rows

        // Inner vertical lines
        for (c in 1 until table.cols) {
            val lx = x + c * cellW
            cs.moveTo(lx, y)
            cs.lineTo(lx, y + h)
            cs.stroke()
        }

        // Inner horizontal lines
        for (r in 1 until table.rows) {
            val ly = y + r * cellH
            cs.moveTo(x, ly)
            cs.lineTo(x + w, ly)
            cs.stroke()
        }

        // Cell texts
        for (r in 0 until table.rows) {
            if (r >= table.cells.size) break
            for (c in 0 until table.cols) {
                if (c >= table.cells[r].size) break
                val text = table.cells[r][c].take(40)
                if (text.isNotBlank()) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 8f)
                    cs.setNonStrokingColor(0f, 0f, 0f)
                    val tx = x + c * cellW + 4f
                    val ty = y + h - (r + 1) * cellH + 4f
                    cs.newLineAtOffset(tx, ty)
                    cs.showText(text)
                    cs.endText()
                }
            }
        }
    }

    private fun drawComment(
        cs: PDPageContentStream,
        comment: Annotation.Comment,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(comment.color)
        val x = comment.x * pageWidth
        val y = pageHeight - (comment.y * pageHeight)

        // Draw pin badge
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
        drawCircle(cs, x, y, 7f)

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8f)
        cs.setNonStrokingColor(1f, 1f, 1f)
        cs.newLineAtOffset(x - 2.5f, y - 2.5f)
        cs.showText("C")
        cs.endText()

        // Text summary preview
        if (comment.text.isNotBlank()) {
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 8f)
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f)
            cs.newLineAtOffset(x + 12f, y - 3f)
            cs.showText(comment.text.take(60).replace("\n", " "))
            cs.endText()
        }
    }

    private fun drawLink(
        page: PDPage,
        cs: PDPageContentStream,
        link: Annotation.Link,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val x = link.x * pageWidth
        val y = pageHeight - (link.y * pageHeight)

        val font = PDType1Font.HELVETICA_BOLD
        val fontSize = 10f
        val text = link.displayText.ifBlank { link.url }
        val textWidth = (font.getStringWidth(text) / 1000f) * fontSize

        // Draw blue text
        cs.beginText()
        cs.setFont(font, fontSize)
        cs.setNonStrokingColor(0.08f, 0.39f, 0.75f)
        cs.newLineAtOffset(x, y)
        cs.showText(text)
        cs.endText()

        // Draw underline
        cs.setStrokingColor(0.08f, 0.39f, 0.75f)
        cs.setLineWidth(1f)
        cs.moveTo(x, y - 2f)
        cs.lineTo(x + textWidth, y - 2f)
        cs.stroke()

        // Create PDF link annotation dictionary
        try {
            val linkAnnot = PDAnnotationLink()
            val rect = PDRectangle()
            rect.lowerLeftX = x
            rect.lowerLeftY = y - 3f
            rect.upperRightX = x + textWidth
            rect.upperRightY = y + fontSize + 2f
            linkAnnot.rectangle = rect

            val action = PDActionURI()
            action.uri = link.url
            linkAnnot.action = action
            page.annotations.add(linkAnnot)
        } catch (_: Exception) {}
    }

    private fun drawSmartGraphic(
        cs: PDPageContentStream,
        graphic: Annotation.SmartGraphic,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(graphic.color)
        val x = graphic.x * pageWidth
        val y = pageHeight - ((graphic.y + graphic.height) * pageHeight)
        val w = graphic.width * pageWidth
        val h = graphic.height * pageHeight

        // Node box
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1.5f)
        cs.addRect(x, y, w, h)
        cs.stroke()

        val title = when (graphic.type) {
            SmartGraphicType.MIND_MAP -> "MindMap Node"
            SmartGraphicType.ORG_CHART -> "OrgChart Box"
        }

        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f)
        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f)
        cs.newLineAtOffset(x + 5f, y + h / 2f - 3f)
        cs.showText(title)
        cs.endText()
    }

    private fun drawTextBox(
        cs: PDPageContentStream,
        tb: Annotation.TextBox,
        pageWidth: Float,
        pageHeight: Float
    ) {
        if (tb.text.isBlank()) return

        val rgb = colorToRgbFloats(tb.color)
        val font = resolveFont(tb.fontFamily, tb.isBold, tb.isItalic)
        val fontSize = tb.fontSize.coerceIn(8f, 36f)

        val x = tb.x * pageWidth
        var y = pageHeight - (tb.y * pageHeight) - fontSize

        val lines = tb.text.split("\n")
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])

        for (line in lines) {
            val sanitized = line.replace("\r", "")
            if (sanitized.isNotEmpty()) {
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.newLineAtOffset(x, y)
                cs.showText(sanitized)
                cs.endText()
            }
            y -= (fontSize * 1.25f)
        }
    }

    private fun resolveFont(fontFamily: TextBoxFont, isBold: Boolean, isItalic: Boolean): PDFont {
        return when (fontFamily) {
            TextBoxFont.SERIF -> when {
                isBold && isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                isBold -> PDType1Font.TIMES_BOLD
                isItalic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            TextBoxFont.MONOSPACE -> when {
                isBold && isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                isBold -> PDType1Font.COURIER_BOLD
                isItalic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
            TextBoxFont.DEFAULT -> when {
                isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                isBold -> PDType1Font.HELVETICA_BOLD
                isItalic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
        }
    }

    private fun drawCircle(
        cs: PDPageContentStream,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        val k = 0.5523f * r
        cs.moveTo(cx, cy + r)
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy)
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r)
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy)
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r)
        cs.fill()
    }

    private fun drawCircleOutline(
        cs: PDPageContentStream,
        cx: Float,
        cy: Float,
        r: Float
    ) {
        val k = 0.5523f * r
        cs.moveTo(cx, cy + r)
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy)
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r)
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy)
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r)
        cs.stroke()
    }

    private fun drawUnderline(
        cs: PDPageContentStream,
        underline: Annotation.Underline,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(underline.color)
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1.5f)
        cs.setLineCapStyle(1)

        val x = underline.x * pageWidth
        val y = pageHeight - (underline.y * pageHeight)
        val w = underline.width * pageWidth

        cs.moveTo(x, y)
        cs.lineTo(x + w, y)
        cs.stroke()
    }

    private fun drawStrikethrough(
        cs: PDPageContentStream,
        strikethrough: Annotation.Strikethrough,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val rgb = colorToRgbFloats(strikethrough.color)
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1.5f)
        cs.setLineCapStyle(1)

        val x = strikethrough.x * pageWidth
        val y = pageHeight - (strikethrough.y * pageHeight)
        val w = strikethrough.width * pageWidth

        cs.moveTo(x, y)
        cs.lineTo(x + w, y)
        cs.stroke()
    }

    private fun colorToRgbFloats(color: Color): FloatArray {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}
