package com.annotatio.maximus.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.annotatio.maximus.model.Annotation
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents a recognized shape type
 */
sealed class RecognizedShape {
    abstract val bounds: RectF
    abstract val center: Offset
    
    data class Line(
        val start: Offset,
        val end: Offset,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Circle(
        val radius: Float,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Ellipse(
        val radiusX: Float,
        val radiusY: Float,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Rectangle(
        val width: Float,
        val height: Float,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Triangle(
        val points: List<Offset>,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Arrow(
        val start: Offset,
        val end: Offset,
        val direction: ArrowDirection,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Star(
        val points: List<Offset>,
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
    
    data class Unknown(
        override val bounds: RectF,
        override val center: Offset
    ) : RecognizedShape()
}

enum class ArrowDirection {
    UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
}

/**
 * Simple Rectangle class for bounds
 */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Shape recognizer for freehand strokes
 */
object ShapeRecognizer {
    
    private const val LINE_STRAIGHTNESS_THRESHOLD = 0.15f
    private const val CORNER_ANGLE_THRESHOLD = 45f
    private const val MIN_POINTS_FOR_SHAPE = 5
    
    /**
     * Recognize a shape from a list of points
     */
    fun recognize(points: List<Offset>): RecognizedShape {
        if (points.size < MIN_POINTS_FOR_SHAPE) {
            return RecognizedShape.Unknown(calculateBounds(points), calculateCenter(points))
        }
        
        // Preprocess points - resample and smooth
        val processedPoints = preprocessPoints(points)
        
        // Calculate basic properties
        val bounds = calculateBounds(processedPoints)
        val center = calculateCenter(processedPoints)
        
        // Check if it's a line
        if (isLine(processedPoints)) {
            return RecognizedShape.Line(
                start = processedPoints.first(),
                end = processedPoints.last(),
                bounds = bounds,
                center = center
            )
        }
        
        // Check if it's closed (start and end are close)
        val isClosed = isClosedShape(processedPoints)
        
        // Detect corners
        val corners = detectCorners(processedPoints)
        
        // Recognize based on corners and shape properties
        return when {
            // Circle detection (closed shape, no corners, roughly circular)
            isClosed && corners.size <= 2 && isCircular(processedPoints, center) -> {
                val (radiusX, radiusY) = calculateRadii(processedPoints, center)
                if (abs(radiusX - radiusY) < max(radiusX, radiusY) * 0.3f) {
                    RecognizedShape.Circle(
                        radius = (radiusX + radiusY) / 2f,
                        bounds = bounds,
                        center = center
                    )
                } else {
                    RecognizedShape.Ellipse(
                        radiusX = radiusX,
                        radiusY = radiusY,
                        bounds = bounds,
                        center = center
                    )
                }
            }
            
            // Rectangle detection (4 corners with ~90 degree angles)
            corners.size == 4 && isRectangle(processedPoints, corners) -> {
                RecognizedShape.Rectangle(
                    width = bounds.width,
                    height = bounds.height,
                    bounds = bounds,
                    center = center
                )
            }
            
            // Triangle detection (3 corners)
            corners.size == 3 -> {
                RecognizedShape.Triangle(
                    points = corners.map { processedPoints[it] },
                    bounds = bounds,
                    center = center
                )
            }
            
            // Arrow detection (not closed, has a head)
            !isClosed && isArrow(processedPoints, corners) -> {
                detectArrow(processedPoints, bounds, center)
            }
            
            // Star detection (closed, many corners)
            isClosed && corners.size in 5..12 && isStarShape(processedPoints, corners) -> {
                RecognizedShape.Star(
                    points = corners.map { processedPoints[it] },
                    bounds = bounds,
                    center = center
                )
            }
            
            else -> RecognizedShape.Unknown(bounds, center)
        }
    }
    
    /**
     * Convert recognized shape to annotation
     */
    fun toAnnotation(
        shape: RecognizedShape,
        pageIndex: Int,
        color: Color,
        strokeWidth: Float
    ): Annotation? {
        return when (shape) {
            is RecognizedShape.Line -> {
                // Calculate normalized coordinates
                val x = min(shape.start.x, shape.end.x)
                val y = (shape.start.y + shape.end.y) / 2f
                val width = abs(shape.end.x - shape.start.x)
                
                // For vertical lines, use different approach
                if (width < 0.01f) {
                    Annotation.Shape(
                        pageIndex = pageIndex,
                        type = com.annotatio.maximus.model.ShapeType.RECTANGLE,
                        x = x - 0.005f,
                        y = min(shape.start.y, shape.end.y),
                        width = 0.01f,
                        height = abs(shape.end.y - shape.start.y),
                        color = color,
                        strokeWidth = strokeWidth
                    )
                } else {
                    // Store as a stroke for now (lines aren't directly supported as shapes)
                    null
                }
            }
            
            is RecognizedShape.Circle -> {
                Annotation.Shape(
                    pageIndex = pageIndex,
                    type = com.annotatio.maximus.model.ShapeType.CIRCLE,
                    x = shape.center.x - shape.radius,
                    y = shape.center.y - shape.radius,
                    width = shape.radius * 2f,
                    height = shape.radius * 2f,
                    color = color,
                    strokeWidth = strokeWidth
                )
            }
            
            is RecognizedShape.Ellipse -> {
                Annotation.Shape(
                    pageIndex = pageIndex,
                    type = com.annotatio.maximus.model.ShapeType.RECTANGLE,
                    x = shape.center.x - shape.radiusX,
                    y = shape.center.y - shape.radiusY,
                    width = shape.radiusX * 2f,
                    height = shape.radiusY * 2f,
                    color = color,
                    strokeWidth = strokeWidth
                )
            }
            
            is RecognizedShape.Rectangle -> {
                Annotation.Shape(
                    pageIndex = pageIndex,
                    type = com.annotatio.maximus.model.ShapeType.RECTANGLE,
                    x = shape.bounds.left,
                    y = shape.bounds.top,
                    width = shape.bounds.width,
                    height = shape.bounds.height,
                    color = color,
                    strokeWidth = strokeWidth
                )
            }
            
            is RecognizedShape.Triangle -> {
                // Create a triangle bounding box
                Annotation.Shape(
                    pageIndex = pageIndex,
                    type = com.annotatio.maximus.model.ShapeType.TRIANGLE,
                    x = shape.bounds.left,
                    y = shape.bounds.top,
                    width = shape.bounds.width,
                    height = shape.bounds.height,
                    color = color,
                    strokeWidth = strokeWidth
                )
            }
            
            else -> null
        }
    }
    
    /**
     * Preprocess points: resample and smooth
     */
    private fun preprocessPoints(points: List<Offset>): List<Offset> {
        // Resample to get evenly spaced points
        val resampled = resamplePoints(points, 64)
        
        // Smooth slightly
        return smoothPoints(resampled)
    }
    
    /**
     * Resample points to get evenly spaced points
     */
    private fun resamplePoints(points: List<Offset>, n: Int): List<Offset> {
        if (points.size <= n) return points
        
        val result = mutableListOf<Offset>()
        result.add(points.first())
        
        val totalLength = calculatePathLength(points)
        val interval = totalLength / (n - 1)
        var currentLength = 0f
        var targetLength = interval
        
        for (i in 1 until points.size) {
            val segment = hypot(
                points[i].x - points[i - 1].x,
                points[i].y - points[i - 1].y
            )
            
            while (currentLength + segment >= targetLength && result.size < n - 1) {
                val t = (targetLength - currentLength) / segment
                result.add(
                    Offset(
                        points[i - 1].x + t * (points[i].x - points[i - 1].x),
                        points[i - 1].y + t * (points[i].y - points[i - 1].y)
                    )
                )
                targetLength += interval
            }
            
            currentLength += segment
        }
        
        result.add(points.last())
        return result
    }
    
    /**
     * Calculate total path length
     */
    private fun calculatePathLength(points: List<Offset>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += hypot(
                points[i].x - points[i - 1].x,
                points[i].y - points[i - 1].y
            )
        }
        return length
    }
    
    /**
     * Smooth points using simple moving average
     */
    private fun smoothPoints(points: List<Offset>): List<Offset> {
        if (points.size < 3) return points
        
        return points.mapIndexed { i, _ ->
            when (i) {
                0 -> points[0]
                points.size - 1 -> points.last()
                else -> {
                    Offset(
                        (points[i - 1].x + points[i].x + points[i + 1].x) / 3f,
                        (points[i - 1].y + points[i].y + points[i + 1].y) / 3f
                    )
                }
            }
        }
    }
    
    /**
     * Check if points form a line
     */
    private fun isLine(points: List<Offset>): Boolean {
        if (points.size < 2) return false
        
        val start = points.first()
        val end = points.last()
        val lineLength = hypot(end.x - start.x, end.y - start.y)
        
        if (lineLength < 0.001f) return false
        
        // Calculate deviation from straight line
        var maxDeviation = 0f
        for (point in points) {
            val deviation = pointToLineDistance(point, start, end)
            maxDeviation = max(maxDeviation, deviation)
        }
        
        return maxDeviation / lineLength < LINE_STRAIGHTNESS_THRESHOLD
    }
    
    /**
     * Calculate point to line distance
     */
    private fun pointToLineDistance(point: Offset, lineStart: Offset, lineEnd: Offset): Float {
        val num = abs(
            (lineEnd.y - lineStart.y) * point.x - 
            (lineEnd.x - lineStart.x) * point.y + 
            lineEnd.x * lineStart.y - 
            lineEnd.y * lineStart.x
        )
        val den = hypot(lineEnd.y - lineStart.y, lineEnd.x - lineStart.x)
        return if (den == 0f) 0f else num / den
    }
    
    /**
     * Check if shape is closed
     */
    private fun isClosedShape(points: List<Offset>): Boolean {
        if (points.size < 3) return false
        val first = points.first()
        val last = points.last()
        val distance = hypot(first.x - last.x, first.y - last.y)
        val pathLength = calculatePathLength(points)
        return distance < pathLength * 0.15f
    }
    
    /**
     * Detect corners in the point sequence
     */
    private fun detectCorners(points: List<Offset>): List<Int> {
        val corners = mutableListOf<Int>()
        val windowSize = max(3, points.size / 20)
        
        for (i in windowSize until points.size - windowSize) {
            val prev = points[i - windowSize]
            val curr = points[i]
            val next = points[i + windowSize]
            
            val angle = calculateAngle(prev, curr, next)
            if (angle < CORNER_ANGLE_THRESHOLD) {
                // Check if this corner is far enough from the previous one
                if (corners.isEmpty() || i - corners.last() > windowSize) {
                    corners.add(i)
                }
            }
        }
        
        return corners
    }
    
    /**
     * Calculate angle at point2
     */
    private fun calculateAngle(p1: Offset, p2: Offset, p3: Offset): Float {
        val angle1 = atan2(p1.y - p2.y, p1.x - p2.x)
        val angle2 = atan2(p3.y - p2.y, p3.x - p2.x)
        var angle = abs(Math.toDegrees((angle2 - angle1).toDouble())).toFloat()
        if (angle > 180) angle = 360 - angle
        return angle
    }
    
    /**
     * Check if shape is roughly circular
     */
    private fun isCircular(points: List<Offset>, center: Offset): Boolean {
        val radii = points.map { hypot(it.x - center.x, it.y - center.y) }
        val avgRadius = radii.average().toFloat()
        if (avgRadius < 0.001f) return false
        
        val variance = radii.map { abs(it - avgRadius) }.average().toFloat()
        return variance / avgRadius < 0.3f
    }
    
    /**
     * Calculate average radii for ellipse detection
     */
    private fun calculateRadii(points: List<Offset>, center: Offset): Pair<Float, Float> {
        val xRadii = points.map { abs(it.x - center.x) }
        val yRadii = points.map { abs(it.y - center.y) }
        return Pair(xRadii.average().toFloat(), yRadii.average().toFloat())
    }
    
    /**
     * Check if shape is a rectangle
     */
    private fun isRectangle(points: List<Offset>, corners: List<Int>): Boolean {
        if (corners.size != 4) return false
        
        // Check angles at corners (should be close to 90 degrees)
        val angles = corners.map { i ->
            val prev = points[(i - 5).coerceAtLeast(0)]
            val curr = points[i]
            val next = points[(i + 5).coerceAtMost(points.size - 1)]
            calculateAngle(prev, curr, next)
        }
        
        return angles.all { abs(it - 90) < 30 }
    }
    
    /**
     * Check if shape might be an arrow
     */
    private fun isArrow(points: List<Offset>, corners: List<Int>): Boolean {
        // Arrow typically has:
        // - A straight line (shaft)
        // - A V-shape or triangle at one end (head)
        // - Or it could be a single stroke with a sharp change in direction
        
        if (corners.isEmpty()) return false
        
        // Check for sharp angle at one end (arrow head)
        val startCorner = corners.first()
        val endCorner = corners.last()
        
        return startCorner < points.size * 0.3 || endCorner > points.size * 0.7
    }
    
    /**
     * Detect arrow direction
     */
    private fun detectArrow(
        points: List<Offset>,
        bounds: RectF,
        center: Offset
    ): RecognizedShape.Arrow {
        val start = points.first()
        val end = points.last()
        
        val dx = end.x - start.x
        val dy = end.y - start.y
        
        val direction = when {
            abs(dx) > abs(dy) -> {
                if (dx > 0) ArrowDirection.RIGHT else ArrowDirection.LEFT
            }
            else -> {
                if (dy > 0) ArrowDirection.DOWN else ArrowDirection.UP
            }
        }
        
        return RecognizedShape.Arrow(
            start = start,
            end = end,
            direction = direction,
            bounds = bounds,
            center = center
        )
    }
    
    /**
     * Check if shape is star-like
     */
    private fun isStarShape(points: List<Offset>, corners: List<Int>): Boolean {
        // Star should have alternating long and short distances from center
        if (corners.size < 5) return false
        
        val center = calculateCenter(points)
        val cornerDistances = corners.map { 
            hypot(points[it].x - center.x, points[it].y - center.y)
        }
        
        val avgDist = cornerDistances.average().toFloat()
        val variations = cornerDistances.map { abs(it - avgDist) / avgDist }
        
        // Stars have alternating peaks and valleys
        return variations.any { it > 0.3f }
    }
    
    /**
     * Calculate bounding box
     */
    private fun calculateBounds(points: List<Offset>): RectF {
        if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        
        for (point in points) {
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }
        
        return RectF(minX, minY, maxX, maxY)
    }
    
    /**
     * Calculate center of points
     */
    private fun calculateCenter(points: List<Offset>): Offset {
        if (points.isEmpty()) return Offset.Zero
        
        val sumX = points.sumOf { it.x.toDouble() }
        val sumY = points.sumOf { it.y.toDouble() }
        
        return Offset(
            (sumX / points.size).toFloat(),
            (sumY / points.size).toFloat()
        )
    }
}
