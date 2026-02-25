package com.annotatio.maximus.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

sealed class Annotation {
    abstract val pageIndex: Int
    abstract val id: String

    data class Stroke(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val path: DrawingPath
    ) : Annotation()

    data class HighlightRect(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Color = Color(0x80FFEB3B)
    ) : Annotation()

    data class TextNote(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val text: String,
        val color: Color = Color(0xFFFFC107)
    ) : Annotation()

    data class Shape(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val type: ShapeType,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val color: Color,
        val strokeWidth: Float
    ) : Annotation()

    data class Underline(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val color: Color = Color.Black
    ) : Annotation()

    data class Strikethrough(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val color: Color = Color.Black
    ) : Annotation()

    data class Table(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rows: Int,
        val cols: Int,
        // cells[row][col] = text
        val cells: List<List<String>>,
        val color: Color = Color.Black
    ) : Annotation()

    data class Image(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        // URI as string for persistence
        val uriString: String
    ) : Annotation()

    data class Link(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val displayText: String,
        val url: String,
        val color: Color = Color(0xFF1565C0)
    ) : Annotation()

    data class Comment(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val text: String,
        val color: Color = Color(0xFFFFC107)
    ) : Annotation()

    data class Signature(
        override val pageIndex: Int,
        override val id: String = UUID.randomUUID().toString(),
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val bitmapData: ByteArray
    ) : Annotation()
}

enum class ShapeType {
    CIRCLE,
    SQUARE,
    RECTANGLE,
    TRIANGLE
}
