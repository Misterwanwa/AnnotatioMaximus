package com.annotatio.maximus.model

/**
 * Represents a line of text on a PDF page with its normalized coordinates.
 * Coordinates are in normalized page space (0..1).
 */
data class TextLine(
    val text: String,
    val x: Float,           // Left position
    val y: Float,           // Baseline Y position
    val width: Float,       // Width of the text line
    val height: Float       // Height of the text line (approximate)
) {
    /**
     * Top of the text line (y - height)
     */
    val top: Float get() = y - height
    
    /**
     * Bottom of the text line (y)
     */
    val bottom: Float get() = y
    
    /**
     * Right edge of the text line (x + width)
     */
    val right: Float get() = x + width
    
    /**
     * Center Y position (for strikethrough) - exactly in the middle between top and baseline
     */
    val centerY: Float get() = (top + y) / 2f
    
    /**
     * Position for underline (slightly below baseline, closer to text)
     */
    val underlineY: Float get() = y + height * 0.1f
    
    /**
     * Checks if the given normalized X coordinate overlaps with this text line horizontally
     */
    fun containsX(normalizedX: Float): Boolean {
        return normalizedX >= x && normalizedX <= right
    }
    
    /**
     * Checks if the given normalized Y coordinate is near this text line vertically
     */
    fun isNearY(normalizedY: Float, tolerance: Float = 0.02f): Boolean {
        return normalizedY >= (top - tolerance) && normalizedY <= (bottom + tolerance)
    }
}

/**
 * Container for all text lines on a page
 */
data class PageTextContent(
    val pageIndex: Int,
    val textLines: List<TextLine>,
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f
) {
    /**
     * Finds the text line closest to the given Y position that also overlaps
     * horizontally with the given X range.
     */
    fun findTextLineForAnnotation(
        startX: Float,
        endX: Float,
        y: Float,
        tolerance: Float = 0.03f
    ): TextLine? {
        // Find lines that overlap horizontally with our selection
        val overlappingLines = textLines.filter { line ->
            // Check if the line overlaps horizontally with our annotation range
            val lineStart = line.x
            val lineEnd = line.right
            val selectionStart = minOf(startX, endX)
            val selectionEnd = maxOf(startX, endX)
            
            // Overlap check: line starts before selection ends AND line ends after selection starts
            lineStart <= selectionEnd && lineEnd >= selectionStart &&
            line.isNearY(y, tolerance)
        }
        
        // Return the line closest to the Y position
        return overlappingLines.minByOrNull { line ->
            kotlin.math.abs(line.centerY - y)
        }
    }
    
    /**
     * Gets all text lines that intersect with the given Y range
     */
    fun getLinesInYRange(yStart: Float, yEnd: Float): List<TextLine> {
        val minY = minOf(yStart, yEnd)
        val maxY = maxOf(yStart, yEnd)
        return textLines.filter { line ->
            line.centerY in minY..maxY || line.top in minY..maxY || line.bottom in minY..maxY
        }
    }
}
