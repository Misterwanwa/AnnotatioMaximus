package com.annotatio.maximus.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.annotatio.maximus.model.PathPoint

@Composable
fun SignatureDialog(
    onSignatureCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var paths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val density = LocalDensity.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unterschrift erstellen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unterschreiben Sie im Feld unten",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints = listOf(offset)
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints = currentPoints + change.position
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    currentPath?.let { paths = paths + it }
                                    currentPath = null
                                    currentPoints = emptyList()
                                },
                                onDragCancel = {
                                    currentPath = null
                                    currentPoints = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw completed paths
                        for (path in paths) {
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw current path
                        currentPath?.let {
                            drawPath(
                                path = it,
                                color = Color.Black,
                                style = Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Create bitmap from the signature
                    val bitmap = Bitmap.createBitmap(
                        with(density) { 400.dp.toPx() }.toInt(),
                        with(density) { 200.dp.toPx() }.toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        strokeWidth = 4f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }

                    for (path in paths + listOfNotNull(currentPath)) {
                        val androidPath = android.graphics.Path()
                        var first = true
                        path.getBounds().let { bounds ->
                            // This is a simplified conversion - in practice you'd need to iterate through path segments
                        }
                        // TODO: Proper path conversion
                    }

                    onSignatureCaptured(bitmap)
                    onDismiss()
                },
                enabled = paths.isNotEmpty() || currentPath != null
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}