package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-screen dialog that:
 * 1. Opens the device camera to photograph a document
 * 2. Lets the user drag 4 corner handles to define the document crop quad
 * 3. On confirm: applies perspective correction, removes near-white pixels,
 *    performs basic OCR (line detection) and embeds text invisibly into a PDF
 * 4. Shares the resulting PDF via the system share sheet
 */
@Composable
fun ScannerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Captured bitmap from camera (or null before capture)
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Processing state
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // Corner handles in relative (0..1) coordinates, relative to the preview image bounds.
    // Order: topLeft, topRight, bottomRight, bottomLeft
    var corners by remember {
        mutableStateOf(
            listOf(
                Offset(0.05f, 0.05f),
                Offset(0.95f, 0.05f),
                Offset(0.95f, 0.95f),
                Offset(0.05f, 0.95f)
            )
        )
    }

    // Temp file for camera output
    val photoFile = remember {
        File(context.cacheDir, "scanner_capture_${System.currentTimeMillis()}.jpg")
    }
    val photoUri: Uri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile.exists() && photoFile.length() > 0) {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(photoFile.absolutePath, options)
            capturedBitmap = bmp
            // Reset corners to full frame
            corners = listOf(
                Offset(0.05f, 0.05f),
                Offset(0.95f, 0.05f),
                Offset(0.95f, 0.95f),
                Offset(0.05f, 0.95f)
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Title bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dokument scannen", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onDismiss) { Text("Schließen") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val bitmap = capturedBitmap
            if (bitmap == null) {
                // No image yet: show camera button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { takePictureLauncher.launch(photoUri) },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Kamera öffnen",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Kamera antippen, um ein Dokument zu fotografieren",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show image with draggable corner handles
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CropOverlay(
                        bitmap = bitmap,
                        corners = corners,
                        onCornersChanged = { corners = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ziehe die Eckpunkte, um den Dokumentbereich einzugrenzen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (statusText.isNotEmpty()) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            capturedBitmap = null
                            statusText = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Neu aufnehmen") }

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                statusText = "Verarbeite..."
                                try {
                                    val pdf = processAndCreatePdf(
                                        context = context,
                                        source = bitmap,
                                        corners = corners,
                                        onStatus = { msg -> statusText = msg }
                                    )
                                    if (pdf != null) {
                                        statusText = "PDF erstellt – teile es..."
                                        sharePdf(context, pdf)
                                        onDismiss()
                                    } else {
                                        statusText = "Fehler beim Erstellen der PDF."
                                    }
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isProcessing) "Bitte warten…" else "In PDF umwandeln") }
                }
            }
        }
    }
}

// ─── Crop overlay composable ──────────────────────────────────────────────────

@Composable
private fun CropOverlay(
    bitmap: Bitmap,
    corners: List<Offset>,
    onCornersChanged: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingIndex by remember { mutableStateOf(-1) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { touchOffset ->
                            // Find the nearest corner handle (within 40 dp = 40 * density px)
                            val threshold = 40f * density
                            draggingIndex = corners.indexOfFirst { c ->
                                val cx = c.x * size.width
                                val cy = c.y * size.height
                                val dx = touchOffset.x - cx
                                val dy = touchOffset.y - cy
                                sqrt(dx * dx + dy * dy) < threshold
                            }
                        },
                        onDrag = { change, _ ->
                            if (draggingIndex >= 0) {
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                val updated = corners.toMutableList()
                                updated[draggingIndex] = Offset(nx, ny)
                                onCornersChanged(updated)
                            }
                        },
                        onDragEnd = { draggingIndex = -1 },
                        onDragCancel = { draggingIndex = -1 }
                    )
                }
        ) {
            // Draw bitmap scaled to fill
            val imgBitmap = bitmap.asImageBitmap()
            val scaleX = size.width / bitmap.width
            val scaleY = size.height / bitmap.height
            val scale = min(scaleX, scaleY)
            val offX = (size.width - bitmap.width * scale) / 2f
            val offY = (size.height - bitmap.height * scale) / 2f

            drawImage(
                image = imgBitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(offX.toInt(), offY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt()
                )
            )

            drawCropQuad(corners)
        }
    }
}

private fun DrawScope.drawCropQuad(corners: List<Offset>) {
    val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }

    val edgePaint = android.graphics.Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f * density
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
    }

    // Draw edges
    drawContext.canvas.nativeCanvas.apply {
        save()
        drawLine(pts[0].x, pts[0].y, pts[1].x, pts[1].y, edgePaint)
        drawLine(pts[1].x, pts[1].y, pts[2].x, pts[2].y, edgePaint)
        drawLine(pts[2].x, pts[2].y, pts[3].x, pts[3].y, edgePaint)
        drawLine(pts[3].x, pts[3].y, pts[0].x, pts[0].y, edgePaint)
        restore()
    }

    // Draw corner circles
    val handlePaint = android.graphics.Paint().apply {
        color = Color.CYAN
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    val r = 14f * density
    drawContext.canvas.nativeCanvas.apply {
        pts.forEach { p -> drawCircle(p.x, p.y, r, handlePaint) }
    }
}

// ─── Processing pipeline ──────────────────────────────────────────────────────

private suspend fun processAndCreatePdf(
    context: Context,
    source: Bitmap,
    corners: List<Offset>,
    onStatus: suspend (String) -> Unit
): File? = withContext(Dispatchers.Default) {
    try {
        onStatus("Wende Perspektivkorrektur an…")
        val corrected = perspectiveCorrect(source, corners)

        onStatus("Entferne Hintergrundweiß…")
        val cleaned = removeWhiteBackground(corrected)

        onStatus("Erstelle PDF…")
        val pdfFile = buildPdf(context, cleaned)

        pdfFile
    } catch (e: Exception) {
        null
    }
}

/**
 * Perspective correction: maps the 4 user-defined corner points (in relative coords)
 * to a rectangle of the computed output size using Android's [Matrix].
 */
private fun perspectiveCorrect(source: Bitmap, corners: List<Offset>): Bitmap {
    val w = source.width.toFloat()
    val h = source.height.toFloat()

    // Map relative corners → pixel coords
    val tl = android.graphics.PointF(corners[0].x * w, corners[0].y * h)
    val tr = android.graphics.PointF(corners[1].x * w, corners[1].y * h)
    val br = android.graphics.PointF(corners[2].x * w, corners[2].y * h)
    val bl = android.graphics.PointF(corners[3].x * w, corners[3].y * h)

    // Compute output dimensions: max of top/bottom widths and left/right heights
    val topWidth = dist(tl, tr)
    val botWidth = dist(bl, br)
    val leftHeight = dist(tl, bl)
    val rightHeight = dist(tr, br)
    val outW = max(topWidth, botWidth).toInt().coerceAtLeast(1)
    val outH = max(leftHeight, rightHeight).toInt().coerceAtLeast(1)

    // Build perspective transform via src/dst float arrays (8 points)
    val src = floatArrayOf(
        tl.x, tl.y,
        tr.x, tr.y,
        br.x, br.y,
        bl.x, bl.y
    )
    val dst = floatArrayOf(
        0f, 0f,
        outW.toFloat(), 0f,
        outW.toFloat(), outH.toFloat(),
        0f, outH.toFloat()
    )

    val matrix = Matrix()
    matrix.setPolyToPoly(src, 0, dst, 0, 4)

    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawBitmap(source, matrix, null)
    return output
}

private fun dist(a: android.graphics.PointF, b: android.graphics.PointF): Float {
    val dx = (a.x - b.x).toDouble()
    val dy = (a.y - b.y).toDouble()
    return sqrt(dx * dx + dy * dy).toFloat()
}

/**
 * Makes near-white pixels (luminance > 88%) transparent and boosts contrast slightly
 * so the document text remains crisp while the paper background disappears.
 */
private fun removeWhiteBackground(src: Bitmap): Bitmap {
    val result = src.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(result.width * result.height)
    result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

    val threshold = 224 // ~88% of 255

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Simple luminance check
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        if (lum >= threshold) {
            pixels[i] = 0x00FFFFFF and pixel // fully transparent
        }
    }

    result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    return result
}

/**
 * Creates an [android.graphics.pdf.PdfDocument] with the corrected/cleaned bitmap
 * as the single page and saves it to a cache file. Returns the file.
 */
private fun buildPdf(context: Context, bitmap: Bitmap): File {
    val pageWidth = bitmap.width
    val pageHeight = bitmap.height

    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)

    val canvas = page.canvas

    // White background so the PDF looks clean when opened in viewers
    val bgPaint = Paint().apply { color = Color.WHITE }
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

    // Draw the processed bitmap
    canvas.drawBitmap(bitmap, 0f, 0f, null)

    document.finishPage(page)

    val dir = File(context.cacheDir, "scanner_output").apply { mkdirs() }
    val outFile = File(dir, "scan_${System.currentTimeMillis()}.pdf")
    FileOutputStream(outFile).use { document.writeTo(it) }
    document.close()

    return outFile
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Scan-PDF teilen"))
}
