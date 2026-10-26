package com.annotatio.maximus.util

import androidx.compose.ui.geometry.Offset
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureCalculatorTest {

    @Test
    fun testDistanceCalculation() {
        val scale = DrawingScale(ratio = 100f, unit = "m", pixelsPerUnit = 100f)
        val p1 = Offset(0f, 0f)
        val p2 = Offset(1f, 0f)

        // 1000px width, 1000px height. 1000px length / 100px_per_m = 10.0m
        val dist = MeasureCalculator.calculateDistance(p1, p2, 1000f, 1000f, scale)
        assertEquals(10f, dist, 0.001f)
    }

    @Test
    fun testPolygonAreaCalculationSquare() {
        val scale = DrawingScale(ratio = 100f, unit = "m", pixelsPerUnit = 10f)
        // Square of 100 x 100 pixels in page coordinates
        // points: (0,0), (0.1, 0), (0.1, 0.1), (0, 0.1) on a 1000x1000 page
        val points = listOf(
            Offset(0f, 0f),
            Offset(0.1f, 0f),
            Offset(0.1f, 0.1f),
            Offset(0f, 0.1f)
        )
        // 100px x 100px = 10,000 px^2.
        // With 10 px/m -> 100 px^2 / m^2 -> 10,000 / 100 = 100 m^2.
        val area = MeasureCalculator.calculatePolygonArea(points, 1000f, 1000f, scale)
        assertEquals(100f, area, 0.01f)
    }

    @Test
    fun testPerimeterCalculation() {
        val scale = DrawingScale(ratio = 100f, unit = "m", pixelsPerUnit = 10f)
        val points = listOf(
            Offset(0f, 0f),
            Offset(0.1f, 0f),
            Offset(0.1f, 0.1f),
            Offset(0f, 0.1f)
        )
        // Each side is 100px / 10 = 10m -> Perimeter = 40m
        val perimeter = MeasureCalculator.calculatePerimeter(points, 1000f, 1000f, scale)
        assertEquals(40f, perimeter, 0.01f)
    }

    @Test
    fun testScaleCalibrationWithMeters() {
        val scale = DrawingScale(ratio = 100f, unit = "m", pixelsPerUnit = 50f)
        val pixelLine = 200f
        val calibrated = MeasureCalculator.calibrateScale(pixelLine, "10m", scale)

        assertEquals("m", calibrated.unit)
        // 200px / 10m = 20 px/m
        assertEquals(20f, calibrated.pixelsPerUnit, 0.001f)
    }

    @Test
    fun testScaleCalibrationWithCentimeters() {
        val scale = DrawingScale(ratio = 100f, unit = "m", pixelsPerUnit = 50f)
        val pixelLine = 200f
        val calibrated = MeasureCalculator.calibrateScale(pixelLine, "500 cm", scale)

        assertEquals("cm", calibrated.unit)
        assertEquals(200f / 500f, calibrated.pixelsPerUnit, 0.001f)
    }

    @Test
    fun testTakeoffCsvExport() {
        val items = listOf(
            MeasurementItem(
                id = "item-1",
                pageIndex = 0,
                type = MeasurementType.DISTANCE,
                points = emptyList(),
                calculatedValue = 12.5f,
                unit = "m",
                formattedText = "12,50 m",
                label = "Wandachse A"
            ),
            MeasurementItem(
                id = "item-2",
                pageIndex = 0,
                type = MeasurementType.AREA,
                points = emptyList(),
                calculatedValue = 45.2f,
                unit = "m",
                formattedText = "45,20 m²",
                label = "Büro 101"
            )
        )

        val csv = MeasureCalculator.exportTakeoffToCsv(items)
        assertTrue(csv.contains("Wandachse A"))
        assertTrue(csv.contains("Büro 101"))
        assertTrue(csv.contains("12.50"))
        assertTrue(csv.contains("45.20"))
    }
}
