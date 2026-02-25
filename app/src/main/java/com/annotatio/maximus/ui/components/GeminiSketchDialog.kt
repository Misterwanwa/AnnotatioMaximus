package com.annotatio.maximus.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private data class SketchStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun GeminiSketchDialog(
    initialColor: Color,
    strokeWidth: Float,
    onDismiss: () -> Unit,
    onSend: (Bitmap) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val gestureModifier = Modifier.pointerInput(initialColor, strokeWidth) {
        detectDragGestures(
            onDragStart = { offset ->
                currentPoints = listOf(offset)
            },
            onDrag = { change, _ ->
                change.consume()
                currentPoints = currentPoints + change.position
            },
            onDragEnd = {
                if (currentPoints.size >= 2) {
                    strokes.add(
                        SketchStroke(
                            points = currentPoints,
                            color = initialColor,
                            strokeWidth = strokeWidth
                        )
                    )
                }
                currentPoints = emptyList()
            },
            onDragCancel = {
                currentPoints = emptyList()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Freihand-Skizze für Gemini") },
        text = {
            Column {
                Text(
                    text = "Zeichne eine Skizze. Sie wird als Bild an Gemini übergeben.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(4.dp)
                        .then(gestureModifier),
                    onDraw = {
                        canvasSize = IntSize(size.width.toInt(), size.height.toInt())

                        // Draw finished strokes
                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            val path = Path()
                            val first = stroke.points.first()
                            path.moveTo(first.x, first.y)
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw current stroke
                        if (currentPoints.size >= 2) {
                            val path = Path()
                            val first = currentPoints.first()
                            path.moveTo(first.x, first.y)
                            for (i in 1 until currentPoints.size) {
                                val pt = currentPoints[i]
                                path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = initialColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
                        onDismiss()
                        return@TextButton
                    }

                    val bitmap = Bitmap.createBitmap(
                        canvasSize.width,
                        canvasSize.height,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(Color.White.toArgb())

                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }

                    fun drawStroke(stroke: SketchStroke) {
                        if (stroke.points.size < 2) return
                        paint.color = stroke.color.toArgb()
                        paint.strokeWidth = stroke.strokeWidth
                        for (i in 1 until stroke.points.size) {
                            val p0 = stroke.points[i - 1]
                            val p1 = stroke.points[i]
                            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
                        }
                    }

                    strokes.forEach(::drawStroke)
                    if (currentPoints.size >= 2) {
                        drawStroke(
                            SketchStroke(
                                points = currentPoints,
                                color = initialColor,
                                strokeWidth = strokeWidth
                            )
                        )
                    }

                    onSend(bitmap)
                }
            ) {
                Text("An Gemini senden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
