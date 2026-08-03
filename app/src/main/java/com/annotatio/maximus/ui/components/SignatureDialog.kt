package com.annotatio.maximus.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream

private const val SIG_DIR = "signatures"
private const val SIG_PREFIX = "sig_"
private const val SIG_EXT = ".png"

fun loadSavedSignatures(context: Context): List<File> {
    val dir = File(context.filesDir, SIG_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.name.startsWith(SIG_PREFIX) && f.name.endsWith(SIG_EXT) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
}

private fun saveSignature(context: Context, bitmap: Bitmap): File {
    val dir = File(context.filesDir, SIG_DIR).apply { mkdirs() }
    val file = File(dir, "$SIG_PREFIX${System.currentTimeMillis()}$SIG_EXT")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/**
 * Converts a list of Compose Offsets into a smooth Android Path using
 * Catmull-Rom spline interpolation to eliminate jitter.
 */
private fun smoothPath(points: List<Offset>): android.graphics.Path {
    val path = android.graphics.Path()
    if (points.isEmpty()) return path
    if (points.size == 1) {
        path.moveTo(points[0].x, points[0].y)
        return path
    }
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    // Catmull-Rom to Bezier conversion
    for (i in 0 until points.size - 1) {
        val p0 = if (i == 0) points[0] else points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

/** Renders a list of stroke point-lists onto a Bitmap. */
private fun renderSignatureToBitmap(
    strokes: List<List<Offset>>,
    widthPx: Int,
    heightPx: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 4f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }
    for (stroke in strokes) {
        if (stroke.isNotEmpty()) {
            canvas.drawPath(smoothPath(stroke), paint)
        }
    }
    return bitmap
}

@Composable
fun SignatureDialog(
    onSignatureCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Completed strokes (list of point lists)
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    // Points of the currently active stroke
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Saved signatures from local storage
    var savedSigs by remember { mutableStateOf(loadSavedSignatures(context)) }

    val canvasWidthDp = 380.dp
    val canvasHeightDp = 200.dp

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
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Drawing canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes = strokes + listOf(currentStroke)
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Render completed strokes
                        for (stroke in strokes) {
                            if (stroke.size >= 2) {
                                val path = Path()
                                path.moveTo(stroke[0].x, stroke[0].y)
                                for (i in 1 until stroke.size) {
                                    val p0 = if (i == 1) stroke[0] else stroke[i - 2]
                                    val p1 = stroke[i - 1]
                                    val p2 = stroke[i]
                                    val p3 = if (i + 1 < stroke.size) stroke[i + 1] else stroke[i]
                                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                                    val cp1y = p1.y + (p2.y - p0.y) / 6f
                                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                                    val cp2y = p2.y - (p3.y - p1.y) / 6f
                                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                        // Render active stroke
                        if (currentStroke.size >= 2) {
                            val path = Path()
                            path.moveTo(currentStroke[0].x, currentStroke[0].y)
                            for (i in 1 until currentStroke.size) {
                                path.lineTo(currentStroke[i].x, currentStroke[i].y)
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                // Undo / Clear row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Rückgängig") }
                    TextButton(
                        onClick = { strokes = emptyList(); currentStroke = emptyList() },
                        enabled = strokes.isNotEmpty()
                    ) { Text("Löschen") }
                }

                // Saved signatures
                if (savedSigs.isNotEmpty()) {
                    Text(
                        "Gespeicherte Unterschriften:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 8.dp)
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedSigs) { file ->
                            val bmp = remember(file) {
                                try { android.graphics.BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
                            }
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp, 60.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                        .clickable {
                                            onSignatureCaptured(bmp)
                                            onDismiss()
                                        }
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Gespeicherte Unterschrift",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = {
                                            file.delete()
                                            savedSigs = loadSavedSignatures(context)
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            val widthPx = with(density) { canvasWidthDp.toPx() }.toInt().coerceAtLeast(1)
                            val heightPx = with(density) { canvasHeightDp.toPx() }.toInt().coerceAtLeast(1)
                            val bitmap = renderSignatureToBitmap(strokes, widthPx, heightPx)
                            saveSignature(context, bitmap)
                            savedSigs = loadSavedSignatures(context)
                            onSignatureCaptured(bitmap)
                            onDismiss()
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) { Text("Speichern & Übernehmen") }
                TextButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            val widthPx = with(density) { canvasWidthDp.toPx() }.toInt().coerceAtLeast(1)
                            val heightPx = with(density) { canvasHeightDp.toPx() }.toInt().coerceAtLeast(1)
                            val bitmap = renderSignatureToBitmap(strokes, widthPx, heightPx)
                            onSignatureCaptured(bitmap)
                            onDismiss()
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) { Text("Übernehmen") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    )
}
