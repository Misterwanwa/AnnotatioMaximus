package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class CreateMode { BLANK, TEXT, IMAGE, HTML }

/**
 * Dialog for creating a new PDF from scratch:
 * - Blank page
 * - Plain text
 * - From image
 * - From HTML (rendered as plain text in a PDF page)
 */
@Composable
fun CreatePdfDialog(
    onDismiss: () -> Unit,
    onPdfCreated: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf<CreateMode?>(null) }
    var textInput by remember { mutableStateOf("") }
    var htmlInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF erstellen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (mode == null) {
                    Text("Wähle eine Quelle:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    CreateOptionRow(Icons.Default.NoteAdd, "Leere Seite", "Erstelle ein neues leeres PDF") { mode = CreateMode.BLANK }
                    Divider()
                    CreateOptionRow(Icons.Default.Article, "Aus Text", "Füge Text ein und wandle ihn in ein PDF um") { mode = CreateMode.TEXT }
                    Divider()
                    CreateOptionRow(Icons.Default.Image, "Aus Bild", "Wähle ein Bild und wandle es in ein PDF um") { mode = CreateMode.IMAGE }
                    Divider()
                    CreateOptionRow(Icons.Default.Code, "Aus HTML", "Füge HTML-Inhalt ein und wandle ihn in ein PDF um") { mode = CreateMode.HTML }
                } else when (mode) {
                    CreateMode.BLANK -> {
                        Text("Eine leere PDF-Seite (A4) wird erstellt.", style = MaterialTheme.typography.bodySmall)
                    }
                    CreateMode.TEXT -> {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text eingeben") },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            maxLines = 20
                        )
                    }
                    CreateMode.IMAGE -> {
                        Text(
                            if (pickedImageUri != null) "Bild ausgewählt ✓" else "Kein Bild ausgewählt",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Bild auswählen")
                        }
                    }
                    CreateMode.HTML -> {
                        OutlinedTextField(
                            value = htmlInput,
                            onValueChange = { htmlInput = it },
                            label = { Text("HTML eingeben") },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            maxLines = 20
                        )
                    }
                    null -> {}
                }
                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (mode == null) {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            } else {
                Row {
                    TextButton(onClick = { mode = null; statusText = "" }) { Text("Zurück") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        scope.launch {
                            statusText = "Erstelle PDF…"
                            val file = withContext(Dispatchers.IO) {
                                when (mode) {
                                    CreateMode.BLANK -> createBlankPdf(context)
                                    CreateMode.TEXT -> createTextPdf(context, textInput)
                                    CreateMode.IMAGE -> pickedImageUri?.let { createImagePdf(context, it) }
                                    CreateMode.HTML -> createHtmlPdf(context, htmlInput)
                                    null -> null
                                }
                            }
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                statusText = "Fertig!"
                                onPdfCreated(uri)
                                onDismiss()
                            } else {
                                statusText = "Fehler beim Erstellen."
                            }
                        }
                    }) { Text("Erstellen") }
                }
            }
        },
        dismissButton = if (mode != null) null else ({
            TextButton(onClick = onDismiss) { Text("Schließen") }
        })
    )
}

@Composable
private fun CreateOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── PDF builders ──────────────────────────────────────────────────────────────

private val A4_W = 595
private val A4_H = 842

private fun createBlankPdf(context: Context): File {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
    val canvas = page.canvas
    canvas.drawColor(Color.WHITE)
    doc.finishPage(page)
    return savePdf(context, doc, "blank")
}

private fun createTextPdf(context: Context, text: String): File {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
    val canvas = page.canvas
    canvas.drawColor(Color.WHITE)

    val paint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }
    val margin = 40f
    val lineHeight = 20f
    val maxWidth = A4_W - 2 * margin
    var y = margin + paint.textSize

    // Simple word-wrap
    val words = text.split(" ", "\n")
    var line = StringBuilder()
    for (word in words) {
        if (word == "\n" || paint.measureText("$line $word") > maxWidth) {
            canvas.drawText(line.toString(), margin, y, paint)
            y += lineHeight
            line = StringBuilder(word)
            if (y > A4_H - margin) break
        } else {
            if (line.isNotEmpty()) line.append(" ")
            line.append(word)
        }
    }
    if (line.isNotEmpty() && y <= A4_H - margin) {
        canvas.drawText(line.toString(), margin, y, paint)
    }

    doc.finishPage(page)
    return savePdf(context, doc, "text")
}

private fun createImagePdf(context: Context, imageUri: Uri): File {
    val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return createBlankPdf(context)

    val scale = minOf(A4_W.toFloat() / bitmap.width, A4_H.toFloat() / bitmap.height)
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)

    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
    val canvas = page.canvas
    canvas.drawColor(Color.WHITE)
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    canvas.drawBitmap(scaled, ((A4_W - w) / 2f), ((A4_H - h) / 2f), null)
    doc.finishPage(page)
    return savePdf(context, doc, "image")
}

private fun createHtmlPdf(context: Context, html: String): File {
    // Strip tags and render as plain text
    val plainText = html.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&")
    return createTextPdf(context, plainText)
}

private fun savePdf(context: Context, doc: PdfDocument, tag: String): File {
    val dir = File(context.cacheDir, "created_pdfs").apply { mkdirs() }
    val file = File(dir, "${tag}_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}
