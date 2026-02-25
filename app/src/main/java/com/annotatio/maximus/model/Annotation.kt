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
