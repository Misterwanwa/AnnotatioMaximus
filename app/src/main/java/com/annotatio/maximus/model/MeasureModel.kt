package com.annotatio.maximus.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID

enum class MeasurementType {
    DISTANCE,
    AREA,
    PERIMETER,
    COUNT,
    REVISION_CLOUD,
    CALLOUT
}

enum class AecToolType(val title: String) {
    NONE("Kein AEC-Tool"),
    DISTANCE("Strecke messen"),
    AREA("Fläche & Umfang"),
    COUNT("Zählwerkzeug"),
    REVISION_CLOUD("Revisionswolke"),
    CALLOUT("Hinweispfeil (Callout)")
}

data class DrawingScale(
    val ratio: Float = 100f,                // z. B. 100 für 1:100
    val unit: String = "m",                 // m, cm, mm, ft
    val pixelsPerUnit: Float = 72f,         // 72 DPI = 1 Inch im PDF; kalibrierbar
    val label: String = "1:100"
)

data class MeasurementItem(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: MeasurementType,
    val points: List<Offset>,               // normalisiert (0..1)
    val calculatedValue: Float,
    val unit: String,
    val formattedText: String,
    val label: String = ""
)
