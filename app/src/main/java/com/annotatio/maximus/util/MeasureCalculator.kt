package com.annotatio.maximus.util

import androidx.compose.ui.geometry.Offset
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.model.MeasurementType
import java.util.Locale
import kotlin.math.hypot

object MeasureCalculator {

    /**
     * Berechnet die reale Distanz zwischen zwei normalisierten Punkten (0..1)
     * bezogen auf die echte PDF-Seitenbreite und Höhe (in PDF-Points).
     */
    fun calculateDistance(
        p1: Offset,
        p2: Offset,
        pageWidth: Float,
        pageHeight: Float,
        scale: DrawingScale
    ): Float {
        val dx = (p2.x - p1.x) * pageWidth
        val dy = (p2.y - p1.y) * pageHeight
        val pixelDist = hypot(dx, dy)
        if (scale.pixelsPerUnit <= 0f) return 0f
        return pixelDist / scale.pixelsPerUnit
    }

    /**
     * Berechnet die reale Fläche eines geschlossenen Polygons via Gaußsche Trapezformel (Shoelace).
     */
    fun calculatePolygonArea(
        points: List<Offset>,
        pageWidth: Float,
        pageHeight: Float,
        scale: DrawingScale
    ): Float {
        if (points.size < 3) return 0f
        var sum = 0.0
        val n = points.size
        for (i in 0 until n) {
            val curr = points[i]
            val next = points[(i + 1) % n]
            val x1 = curr.x * pageWidth.toDouble()
            val y1 = curr.y * pageHeight.toDouble()
            val x2 = next.x * pageWidth.toDouble()
            val y2 = next.y * pageHeight.toDouble()
            sum += (x1 * y2) - (x2 * y1)
        }
        val pixelArea = kotlin.math.abs(sum) / 2.0
        val unitConversion = (scale.pixelsPerUnit * scale.pixelsPerUnit).toDouble()
        if (unitConversion <= 0.0) return 0f
        return (pixelArea / unitConversion).toFloat()
    }

    /**
     * Berechnet den Umfang eines Polygons.
     */
    fun calculatePerimeter(
        points: List<Offset>,
        pageWidth: Float,
        pageHeight: Float,
        scale: DrawingScale
    ): Float {
        if (points.size < 2) return 0f
        var totalDist = 0f
        for (i in 0 until points.size) {
            val nextIdx = (i + 1) % points.size
            totalDist += calculateDistance(points[i], points[nextIdx], pageWidth, pageHeight, scale)
        }
        return totalDist
    }

    /**
     * Kalibriert den Maßstab anhand einer gemessenen Referenzlinie und einer realen Längeneingabe
     * (z. B. "12.3m", "500 cm", "15 ft").
     */
    fun calibrateScale(
        pixelLength: Float,
        rawInput: String,
        currentScale: DrawingScale
    ): DrawingScale {
        val trimmed = rawInput.trim().lowercase(Locale.ROOT)
        var unit = currentScale.unit
        var numStr = trimmed

        if (trimmed.endsWith("mm")) {
            unit = "mm"
            numStr = trimmed.removeSuffix("mm")
        } else if (trimmed.endsWith("cm")) {
            unit = "cm"
            numStr = trimmed.removeSuffix("cm")
        } else if (trimmed.endsWith("m")) {
            unit = "m"
            numStr = trimmed.removeSuffix("m")
        } else if (trimmed.endsWith("ft")) {
            unit = "ft"
            numStr = trimmed.removeSuffix("ft")
        } else if (trimmed.endsWith("in")) {
            unit = "in"
            numStr = trimmed.removeSuffix("in")
        }

        val targetVal = numStr.trim().replace(",", ".").toFloatOrNull() ?: 1f
        if (targetVal <= 0f || pixelLength <= 0f) return currentScale

        val newPixelsPerUnit = pixelLength / targetVal
        val label = "1:${(targetVal * 100f / (pixelLength / 72f)).toInt().coerceAtLeast(1)}"

        return currentScale.copy(
            unit = unit,
            pixelsPerUnit = newPixelsPerUnit,
            label = label
        )
    }

    /**
     * Formatiert eine Messung für die Anzeige.
     */
    fun formatMeasurement(type: MeasurementType, value: Float, unit: String): String {
        return when (type) {
            MeasurementType.DISTANCE, MeasurementType.PERIMETER -> {
                String.format(Locale.GERMANY, "%.2f %s", value, unit)
            }
            MeasurementType.AREA -> {
                String.format(Locale.GERMANY, "%.2f %s²", value, unit)
            }
            MeasurementType.COUNT -> {
                String.format(Locale.GERMANY, "Nr. %d", value.toInt())
            }
            MeasurementType.REVISION_CLOUD -> "Revision"
            MeasurementType.CALLOUT -> "Callout"
        }
    }

    /**
     * Erstellt eine CSV-Aufmaßliste aus allen Messungen (Takeoff Schedule).
     */
    fun exportTakeoffToCsv(items: List<MeasurementItem>): String {
        val sb = StringBuilder()
        sb.append("ID;Seite;Typ;Wert;Einheit;Bezeichnung;Formatiert\n")
        for (item in items) {
            sb.append("${item.id};")
            sb.append("${item.pageIndex + 1};")
            sb.append("${item.type.name};")
            sb.append(String.format(Locale.ROOT, "%.2f", item.calculatedValue)).append(";")
            sb.append("${item.unit};")
            sb.append("\"${item.label.replace("\"", "\"\"")}\";")
            sb.append("\"${item.formattedText}\"\n")
        }
        return sb.toString()
    }
}
