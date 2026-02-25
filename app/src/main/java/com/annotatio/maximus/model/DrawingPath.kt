package com.annotatio.maximus.model

import androidx.compose.ui.graphics.Color

data class PathPoint(
    val x: Float,
    val y: Float
)

data class DrawingPath(
    val points: List<PathPoint>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)
