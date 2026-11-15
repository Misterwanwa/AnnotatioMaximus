package com.annotatio.maximus.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.model.MeasurementType
import com.annotatio.maximus.model.ShapeType
import com.annotatio.maximus.model.SmartGraphicType
import com.annotatio.maximus.model.TextBoxFont
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
        annotations: Map<Int, List<Annotation>>,
        takeoffItems: List<MeasurementItem> = emptyList()
    ) {
        PDFBoxResourceLoader.init(context)

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            try {
                val takeoffByPage = takeoffItems.groupBy { it.pageIndex }
                val allPages = (annotations.keys + takeoffByPage.keys).distinct().sorted()

                for (pageIndex in allPages) {
                    if (pageIndex !in 0 until document.numberOfPages) continue
                    val pageAnnotations = annotations[pageIndex].orEmpty()
                    val pageMeasurements = takeoffByPage[pageIndex].orEmpty()
                    if (pageAnnotations.isEmpty() && pageMeasurements.isEmpty()) continue

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

                        for (item in pageMeasurements) {
                            drawMeasurement(contentStream, item, pageWidth, pageHeight)
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

        val font = PDType1Font.HELVETICA
        val safeText = sanitizeForPdf(note.text.take(80), font)
        if (safeText.isNotEmpty()) {
            cs.beginText()
            cs.setFont(font, 8f)
            cs.setNonStrokingColor(0f, 0f, 0f)
            cs.newLineAtOffset(x + 10f, y - 3f)
            cs.showText(safeText)
            cs.endText()
        }
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
            ShapeType.RECTANGLE -> {
                cs.addRect(x, y, w, h)
                cs.stroke()
            }
            ShapeType.SQUARE -> {
                val side = minOf(w, h)
                cs.addRect(x, y + (h - side), side, side)
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
        val cellFont = PDType1Font.HELVETICA
        for (r in 0 until table.rows) {
            if (r >= table.cells.size) break
            for (c in 0 until table.cols) {
                if (c >= table.cells[r].size) break
                val text = sanitizeForPdf(table.cells[r][c].take(40), cellFont)
                if (text.isNotBlank()) {
                    cs.beginText()
                    cs.setFont(cellFont, 8f)
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
        val commentFont = PDType1Font.HELVETICA
        val safeComment = sanitizeForPdf(comment.text.take(60), commentFont)
        if (safeComment.isNotBlank()) {
            cs.beginText()
            cs.setFont(commentFont, 8f)
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f)
            cs.newLineAtOffset(x + 12f, y - 3f)
            cs.showText(safeComment)
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
        val text = sanitizeForPdf(link.displayText.ifBlank { link.url }, font)
        if (text.isBlank()) return
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
        val topY = pageHeight - (graphic.y * pageHeight)
        val w = graphic.width * pageWidth
        val h = graphic.height * pageHeight
        val bottomY = topY - h

        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1.5f)

        val fillGs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.14f }
        val resetGs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1f }

        when (graphic.type) {
            SmartGraphicType.MIND_MAP -> {
                val cx = x + w / 2f
                val cy = bottomY + h / 2f
                val nodeR = minOf(w, h) * 0.12f
                val branchR = nodeR * 0.7f
                val armLength = minOf(w, h) * 0.30f

                cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                cs.setGraphicsStateParameters(fillGs)
                drawCircle(cs, cx, cy, nodeR)
                cs.setGraphicsStateParameters(resetGs)
                drawCircleOutline(cs, cx, cy, nodeR)

                val angles = listOf(0f, 72f, 144f, 216f, 288f)
                for (angleDeg in angles) {
                    val rad = (angleDeg * PI / 180.0)
                    val bx = cx + cos(rad).toFloat() * armLength
                    val by = cy - sin(rad).toFloat() * armLength
                    cs.moveTo(cx, cy)
                    cs.lineTo(bx, by)
                    cs.stroke()
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.setGraphicsStateParameters(fillGs)
                    drawCircle(cs, bx, by, branchR)
                    cs.setGraphicsStateParameters(resetGs)
                    drawCircleOutline(cs, bx, by, branchR)
                }
            }

            SmartGraphicType.ORG_CHART -> {
                val boxW = w * 0.22f
                val boxH = h * 0.16f

                fun drawOrgBoxPdf(bx: Float, byBottom: Float) {
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.setGraphicsStateParameters(fillGs)
                    cs.addRect(bx, byBottom, boxW, boxH)
                    cs.fill()
                    cs.setGraphicsStateParameters(resetGs)
                    cs.addRect(bx, byBottom, boxW, boxH)
                    cs.stroke()
                }

                // Root box near top
                val rootBottomY = topY - h * 0.05f - boxH
                val rootCx = x + w / 2f
                drawOrgBoxPdf(rootCx - boxW / 2f, rootBottomY)

                // Level 2: 3 boxes
                val level2BottomY = topY - h * 0.45f - boxH
                val level2Xs = listOf(
                    x + w * 0.12f,
                    x + w * 0.5f - boxW / 2f,
                    x + w * 0.88f - boxW
                )
                for (lx in level2Xs) {
                    val nodeCx = lx + boxW / 2f
                    cs.moveTo(rootCx, rootBottomY)
                    cs.lineTo(nodeCx, level2BottomY + boxH)
                    cs.stroke()
                    drawOrgBoxPdf(lx, level2BottomY)
                }

                // Level 3: 1 child under middle level-2 box
                val midL2Cx = level2Xs[1] + boxW / 2f
                val level3BottomY = topY - h * 0.78f - boxH
                cs.moveTo(midL2Cx, level2BottomY)
                cs.lineTo(midL2Cx, level3BottomY + boxH)
                cs.stroke()
                drawOrgBoxPdf(midL2Cx - boxW / 2f, level3BottomY)
            }
        }
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
        val maxW = (tb.width * pageWidth).coerceAtLeast(fontSize * 3f)

        val x = tb.x * pageWidth
        var y = pageHeight - (tb.y * pageHeight) - fontSize

        val wrappedLines = mutableListOf<String>()
        for (rawParagraph in tb.text.replace("\r", "").split("\n")) {
            val cleanParagraph = sanitizeForPdf(rawParagraph, font)
            if (cleanParagraph.isEmpty()) {
                wrappedLines.add("")
                continue
            }
            val words = cleanParagraph.split(" ")
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                val candidateW = (font.getStringWidth(candidate) / 1000f) * fontSize
                if (candidateW <= maxW || current.isEmpty()) {
                    current = StringBuilder(candidate)
                } else {
                    wrappedLines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) wrappedLines.add(current.toString())
        }

        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth((fontSize * 0.07f).coerceAtLeast(0.8f))

        for (line in wrappedLines) {
            if (line.isNotEmpty()) {
                val lineW = (font.getStringWidth(line) / 1000f) * fontSize
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.newLineAtOffset(x, y)
                cs.showText(line)
                cs.endText()

                if (tb.isUnderline) {
                    cs.moveTo(x, y - fontSize * 0.12f)
                    cs.lineTo(x + lineW, y - fontSize * 0.12f)
                    cs.stroke()
                }
                if (tb.isStrikethrough) {
                    cs.moveTo(x, y + fontSize * 0.30f)
                    cs.lineTo(x + lineW, y + fontSize * 0.30f)
                    cs.stroke()
                }
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

    private fun drawMeasurement(
        cs: PDPageContentStream,
        item: MeasurementItem,
        pageWidth: Float,
        pageHeight: Float
    ) {
        if (item.points.isEmpty()) return
        val accentColor = when (item.type) {
            MeasurementType.DISTANCE -> Color(0xFFE53935)
            MeasurementType.AREA -> Color(0xFF1E88E5)
            MeasurementType.PERIMETER -> Color(0xFF8E24AA)
            MeasurementType.COUNT -> Color(0xFF43A047)
            MeasurementType.REVISION_CLOUD -> Color(0xFFFB8C00)
            MeasurementType.CALLOUT -> Color(0xFFD81B60)
        }
        val rgb = colorToRgbFloats(accentColor)
        val pts = item.points.map { it.x * pageWidth to (pageHeight - it.y * pageHeight) }
        val font = PDType1Font.HELVETICA_BOLD
        val displayLabel = item.formattedText.ifBlank {
            item.label.ifBlank {
                MeasureCalculator.formatMeasurement(item.type, item.calculatedValue, item.unit)
            }
        }

        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
        cs.setLineWidth(1.8f)

        fun drawPillLabel(cx: Float, cy: Float, rawLabel: String) {
            val text = sanitizeForPdf(rawLabel, font)
            if (text.isBlank()) return
            val fontSize = 8f
            val textW = (font.getStringWidth(text) / 1000f) * fontSize
            val padX = 4f
            val pillW = textW + padX * 2f
            val pillH = 12f
            cs.setNonStrokingColor(0.12f, 0.13f, 0.16f)
            cs.addRect(cx - pillW / 2f, cy - pillH / 2f, pillW, pillH)
            cs.fill()
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
            cs.setLineWidth(0.8f)
            cs.addRect(cx - pillW / 2f, cy - pillH / 2f, pillW, pillH)
            cs.stroke()
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.setNonStrokingColor(1f, 1f, 1f)
            cs.newLineAtOffset(cx - textW / 2f, cy - fontSize * 0.3f)
            cs.showText(text)
            cs.endText()
        }

        when (item.type) {
            MeasurementType.DISTANCE -> {
                if (pts.size < 2) return
                val (x0, y0) = pts[0]
                val (x1, y1) = pts[1]
                cs.moveTo(x0, y0)
                cs.lineTo(x1, y1)
                cs.stroke()
                val dx = x1 - x0
                val dy = y1 - y0
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val nx = -dy / len * 5f
                val ny = dx / len * 5f
                cs.moveTo(x0 - nx, y0 - ny); cs.lineTo(x0 + nx, y0 + ny); cs.stroke()
                cs.moveTo(x1 - nx, y1 - ny); cs.lineTo(x1 + nx, y1 + ny); cs.stroke()
                drawPillLabel((x0 + x1) / 2f, (y0 + y1) / 2f + 10f, displayLabel)
            }

            MeasurementType.AREA -> {
                if (pts.size < 3) return
                val fillGs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.2f }
                val resetGs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1f }
                cs.setGraphicsStateParameters(fillGs)
                cs.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) cs.lineTo(pts[i].first, pts[i].second)
                cs.closePath()
                cs.fill()
                cs.setGraphicsStateParameters(resetGs)

                cs.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) cs.lineTo(pts[i].first, pts[i].second)
                cs.closePath()
                cs.stroke()

                val cx = pts.map { it.first }.average().toFloat()
                val cy = pts.map { it.second }.average().toFloat()
                drawPillLabel(cx, cy, displayLabel)
            }

            MeasurementType.PERIMETER -> {
                if (pts.size < 2) return
                cs.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) cs.lineTo(pts[i].first, pts[i].second)
                cs.stroke()

                val cx = pts.map { it.first }.average().toFloat()
                val cy = pts.map { it.second }.average().toFloat()
                drawPillLabel(cx, cy, displayLabel)
            }

            MeasurementType.COUNT -> {
                val (cx, cy) = pts.first()
                val r = 9f
                drawCircle(cs, cx, cy, r)
                cs.setStrokingColor(1f, 1f, 1f)
                cs.setLineWidth(1.2f)
                drawCircleOutline(cs, cx, cy, r)
                val text = sanitizeForPdf(item.label.ifBlank { "#${item.calculatedValue.toInt()}" }, font)
                if (text.isNotBlank()) {
                    val fontSize = 7.5f
                    val textW = (font.getStringWidth(text) / 1000f) * fontSize
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(1f, 1f, 1f)
                    cs.newLineAtOffset(cx - textW / 2f, cy - fontSize * 0.3f)
                    cs.showText(text)
                    cs.endText()
                }
            }

            MeasurementType.REVISION_CLOUD -> {
                if (pts.size < 2) return
                val left = min(pts[0].first, pts[1].first)
                val right = max(pts[0].first, pts[1].first)
                val bottom = min(pts[0].second, pts[1].second)
                val top = max(pts[0].second, pts[1].second)
                val w = right - left
                val h = top - bottom
                val radius = (minOf(w, h) * 0.14f).coerceIn(5f, 12f)

                fun scallopedEdge(sx: Float, sy: Float, ex: Float, ey: Float) {
                    val dx = ex - sx
                    val dy = ey - sy
                    val len = hypot(dx, dy)
                    if (len < 1f) return
                    val count = max(1, (len / (radius * 1.6f)).toInt())
                    val stepX = dx / count
                    val stepY = dy / count
                    val ux = -dy / len
                    val uy = dx / len
                    cs.moveTo(sx, sy)
                    for (i in 0 until count) {
                        val p0x = sx + stepX * i
                        val p0y = sy + stepY * i
                        val p2x = sx + stepX * (i + 1)
                        val p2y = sy + stepY * (i + 1)
                        val cx = (p0x + p2x) / 2f + ux * radius * 0.9f
                        val cy = (p0y + p2y) / 2f + uy * radius * 0.9f
                        val cp1x = p0x + (2f / 3f) * (cx - p0x)
                        val cp1y = p0y + (2f / 3f) * (cy - p0y)
                        val cp2x = p2x + (2f / 3f) * (cx - p2x)
                        val cp2y = p2y + (2f / 3f) * (cy - p2y)
                        cs.curveTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
                    }
                    cs.stroke()
                }

                scallopedEdge(left, top, right, top)
                scallopedEdge(right, top, right, bottom)
                scallopedEdge(right, bottom, left, bottom)
                scallopedEdge(left, bottom, left, top)
            }

            MeasurementType.CALLOUT -> {
                if (pts.size < 2) return
                val (tipX, tipY) = pts[0]
                val (ancX, ancY) = pts[1]
                cs.moveTo(ancX, ancY)
                cs.lineTo(tipX, tipY)
                cs.stroke()

                val angle = atan2(tipY - ancY, tipX - ancX)
                val headLen = 8f
                val spread = (28f * PI / 180.0)
                val p1x = tipX - headLen * cos(angle - spread).toFloat()
                val p1y = tipY - headLen * sin(angle - spread).toFloat()
                val p2x = tipX - headLen * cos(angle + spread).toFloat()
                val p2y = tipY - headLen * sin(angle + spread).toFloat()
                cs.moveTo(tipX, tipY)
                cs.lineTo(p1x, p1y)
                cs.lineTo(p2x, p2y)
                cs.closePath()
                cs.fill()

                val text = sanitizeForPdf(
                    item.label.ifBlank { item.formattedText.ifBlank { "Callout" } },
                    font
                )
                val fontSize = 8f
                val textW = (font.getStringWidth(text) / 1000f) * fontSize
                val padX = 5f
                val boxW = textW + padX * 2f
                val boxH = 13f
                val boxLeft = ancX - boxW / 2f
                val boxBottom = ancY - boxH / 2f
                cs.addRect(boxLeft, boxBottom, boxW, boxH)
                cs.fill()
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.setNonStrokingColor(1f, 1f, 1f)
                cs.newLineAtOffset(boxLeft + padX, ancY - fontSize * 0.3f)
                cs.showText(text)
                cs.endText()
            }
        }
    }

    private fun sanitizeForPdf(text: String, font: PDFont): String {
        val normalized = text
            .replace("\r", "")
            .replace("\n", " ")
            .replace("\u2026", "...")
        val sb = StringBuilder(normalized.length)
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            val charCount = Character.charCount(cp)
            val ch = String(Character.toChars(cp))
            try {
                font.encode(ch)
                sb.append(ch)
            } catch (_: Exception) {
                if (cp == 0x09) {
                    sb.append(' ')
                } else if (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT.toInt()) {
                    sb.append('?')
                }
            }
            i += charCount
        }
        return sb.toString()
    }

    private fun colorToRgbFloats(color: Color): FloatArray {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}
