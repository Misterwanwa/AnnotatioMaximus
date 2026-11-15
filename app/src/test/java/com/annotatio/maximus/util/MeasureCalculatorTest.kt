package com.annotatio.maximus.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.model.MeasurementType
import com.annotatio.maximus.model.ShapeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

    @Test
    fun testShapeRecognizerLineOnNormalizedCoords() {
        val points = (0..20).map { i ->
            val t = i / 20f
            Offset(0.1f + t * 0.7f, 0.2f + t * 0.4f)
        }
        val recognized = ShapeRecognizer.recognize(points)
        assertTrue("Expected Line, got $recognized", recognized is RecognizedShape.Line)

        val annotation = ShapeRecognizer.toAnnotation(recognized, pageIndex = 0, color = Color.Red, strokeWidth = 3f)
        assertTrue(annotation is Annotation.Stroke)
        val stroke = annotation as Annotation.Stroke
        assertEquals(2, stroke.path.points.size)
        assertTrue(stroke.path.points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun testShapeRecognizerCircleOnNormalizedCoords() {
        val points = (0..64).map { i ->
            val angle = (2.0 * PI * i / 64.0)
            Offset(0.5f + 0.2f * cos(angle).toFloat(), 0.5f + 0.2f * sin(angle).toFloat())
        }
        val recognized = ShapeRecognizer.recognize(points)
        assertTrue("Expected Circle, got $recognized", recognized is RecognizedShape.Circle)

        val annotation = ShapeRecognizer.toAnnotation(recognized, pageIndex = 1, color = Color.Blue, strokeWidth = 2f)
        assertTrue(annotation is Annotation.Shape)
        val shape = annotation as Annotation.Shape
        assertEquals(ShapeType.CIRCLE, shape.type)
        assertEquals(1, shape.pageIndex)
    }

    @Test
    fun testShapeRecognizerRectangleOnNormalizedCoords() {
        val vertices = listOf(
            Offset(0.2f, 0.2f),
            Offset(0.8f, 0.2f),
            Offset(0.8f, 0.7f),
            Offset(0.2f, 0.7f),
            Offset(0.2f, 0.2f)
        )
        val points = interpolatePolygon(vertices, stepsPerEdge = 16)
        val recognized = ShapeRecognizer.recognize(points)
        assertTrue("Expected Rectangle, got $recognized", recognized is RecognizedShape.Rectangle)

        val annotation = ShapeRecognizer.toAnnotation(recognized, pageIndex = 0, color = Color.Green, strokeWidth = 2f)
        assertTrue(annotation is Annotation.Shape)
        assertEquals(ShapeType.RECTANGLE, (annotation as Annotation.Shape).type)
    }

    @Test
    fun testShapeRecognizerTriangleOnNormalizedCoords() {
        val vertices = listOf(
            Offset(0.5f, 0.2f),
            Offset(0.8f, 0.8f),
            Offset(0.2f, 0.8f),
            Offset(0.5f, 0.2f)
        )
        val points = interpolatePolygon(vertices, stepsPerEdge = 20)
        val recognized = ShapeRecognizer.recognize(points)
        assertTrue("Expected Triangle, got $recognized", recognized is RecognizedShape.Triangle)

        val annotation = ShapeRecognizer.toAnnotation(recognized, pageIndex = 0, color = Color.Black, strokeWidth = 2f)
        assertTrue(annotation is Annotation.Shape)
        assertEquals(ShapeType.TRIANGLE, (annotation as Annotation.Shape).type)
    }

    private fun interpolatePolygon(vertices: List<Offset>, stepsPerEdge: Int): List<Offset> {
        val pts = mutableListOf<Offset>()
        for (e in 0 until vertices.size - 1) {
            val a = vertices[e]
            val b = vertices[e + 1]
            for (s in 0 until stepsPerEdge) {
                val t = s.toFloat() / stepsPerEdge.toFloat()
                pts.add(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        pts.add(vertices.last())
        return pts
    }
}
