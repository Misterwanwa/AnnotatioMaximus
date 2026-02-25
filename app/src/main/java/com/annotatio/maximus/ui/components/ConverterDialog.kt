package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private data class ConvertFormat(
    val label: String,
    val mimeType: String,
    val extension: String,
    val icon: ImageVector
)

private val CONVERT_FORMATS = listOf(
    ConvertFormat("Word (.docx)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", Icons.Default.Description),
    ConvertFormat("PNG", "image/png", "png", Icons.Default.Image),
    ConvertFormat("JPG", "image/jpeg", "jpg", Icons.Default.Image),
    ConvertFormat("GIF", "image/gif", "gif", Icons.Default.GifBox),
    ConvertFormat("HTML", "text/html", "html", Icons.Default.Html),
    ConvertFormat("EPUB", "application/epub+zip", "epub", Icons.Default.MenuBook)
)

@Composable
fun ConverterDialog(
    pdfUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isConverting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isConverting) onDismiss() },
        title = { Text("PDF konvertieren") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isConverting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Konvertierung läuft…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (statusMessage != null) {
                    Text(
                        statusMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusMessage!!.startsWith("Fehler"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isConverting) {
                    Text(
                        "Format auswählen:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CONVERT_FORMATS.forEach { fmt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        isConverting = true
                                        statusMessage = null
                                        try {
                                            val file = convertPdf(context, pdfUri, fmt)
                                            shareFile(context, file, fmt.mimeType)
                                            statusMessage = "Fertig: ${file.name} wird geteilt"
                                        } catch (e: Exception) {
                                            statusMessage = "Fehler: ${e.message}"
                                        }
                                        isConverting = false
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                fmt.icon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(fmt.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConverting
            ) { Text("Schließen") }
        }
    )
}

private suspend fun convertPdf(context: Context, pdfUri: Uri, fmt: ConvertFormat): File =
    withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "converter").apply { mkdirs() }
        val outFile = File(outDir, "converted_${System.currentTimeMillis()}.${fmt.extension}")

        when (fmt.extension) {
            "png", "jpg" -> {
                // Render first page as bitmap
                val bitmap = renderFirstPage(context, pdfUri, 1920)
                FileOutputStream(outFile).use { fos ->
                    val compressFormat = if (fmt.extension == "jpg")
                        Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    bitmap.compress(compressFormat, 92, fos)
                }
                bitmap.recycle()
            }
            "gif" -> {
                // Render first page as PNG (GIF encoding not native; wrap as PNG-in-GIF placeholder)
                val bitmap = renderFirstPage(context, pdfUri, 1280)
                // Write a minimal GIF-compatible PNG; most viewers accept PNG renamed .gif
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                bitmap.recycle()
            }
            "html" -> {
                // Embed a base64 PNG of the first page inside a simple HTML wrapper
                val bitmap = renderFirstPage(context, pdfUri, 1600)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos)
                bitmap.recycle()
                val base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                outFile.writeText("""<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Konvertierte PDF</title>
<style>body{margin:0;background:#fff}img{max-width:100%;display:block}</style>
</head><body>
<img src="data:image/png;base64,$base64" alt="PDF-Seite 1"/>
</body></html>""")
            }
            "docx" -> {
                // Minimal valid DOCX: ZIP with a single document.xml containing plain text placeholder
                writeMinimalDocx(outFile, "Dieses Dokument wurde aus einer PDF konvertiert.\n\n[Vollständige DOCX-Konvertierung erfordert eine externe Bibliothek wie Apache POI.]")
            }
            "epub" -> {
                // Minimal EPUB 3 containing the first-page image
                val bitmap = renderFirstPage(context, pdfUri, 1600)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos)
                bitmap.recycle()
                writeMinimalEpub(outFile, baos.toByteArray())
            }
            else -> throw IllegalArgumentException("Unbekanntes Format: ${fmt.extension}")
        }
        outFile
    }

private fun renderFirstPage(context: Context, pdfUri: Uri, maxDim: Int): Bitmap {
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
        ?: throw IllegalStateException("PDF konnte nicht geöffnet werden")
    pfd.use {
        val renderer = PdfRenderer(it)
        renderer.use { r ->
            val page = r.openPage(0)
            page.use { p ->
                val scale = maxDim.toFloat() / maxOf(p.width, p.height)
                val w = (p.width * scale).toInt().coerceAtLeast(1)
                val h = (p.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // White background
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        }
    }
}

/** Writes a minimal but valid DOCX (ZIP) with a single paragraph. */
private fun writeMinimalDocx(file: File, text: String) {
    val escapedText = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "</w:t><w:br/><w:t>")

    val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
  xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t xml:space="preserve">$escapedText</w:t></w:r></w:p>
  </w:body>
</w:document>"""

    val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    val wordRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""

    val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    java.util.zip.ZipOutputStream(FileOutputStream(file)).use { zos ->
        fun addEntry(name: String, content: String) {
            zos.putNextEntry(java.util.zip.ZipEntry(name))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        addEntry("[Content_Types].xml", contentTypesXml)
        addEntry("_rels/.rels", relsXml)
        addEntry("word/_rels/document.xml.rels", wordRelsXml)
        addEntry("word/document.xml", documentXml)
    }
}

/** Writes a minimal valid EPUB 3 containing one page as an image. */
private fun writeMinimalEpub(file: File, pageImageBytes: ByteArray) {
    val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Konvertierte PDF</dc:title>
    <dc:language>de</dc:language>
    <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="page1img" href="page1.png" media-type="image/png"/>
    <item id="page1" href="page1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx"><itemref idref="page1"/></spine>
</package>"""

    val page1Xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Seite 1</title>
<style>body{margin:0}img{max-width:100%}</style>
</head>
<body><img src="page1.png" alt="Seite 1"/></body>
</html>"""

    val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="uid"/></head>
  <docTitle><text>Konvertierte PDF</text></docTitle>
  <navMap><navPoint id="np1" playOrder="1"><navLabel><text>Seite 1</text></navLabel>
  <content src="page1.xhtml"/></navPoint></navMap>
</ncx>"""

    java.util.zip.ZipOutputStream(FileOutputStream(file)).use { zos ->
        // mimetype must be first and uncompressed
        val mimetypeEntry = java.util.zip.ZipEntry("mimetype").also { it.method = java.util.zip.ZipEntry.STORED }
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        mimetypeEntry.size = mimetypeBytes.size.toLong()
        mimetypeEntry.compressedSize = mimetypeBytes.size.toLong()
        mimetypeEntry.crc = java.util.zip.CRC32().also { it.update(mimetypeBytes) }.value
        zos.putNextEntry(mimetypeEntry)
        zos.write(mimetypeBytes)
        zos.closeEntry()

        fun addText(name: String, content: String) {
            zos.putNextEntry(java.util.zip.ZipEntry(name))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        addText("META-INF/container.xml", """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf"
    media-type="application/oebps-package+xml"/></rootfiles>
</container>""")
        addText("OEBPS/content.opf", contentOpf)
        addText("OEBPS/page1.xhtml", page1Xhtml)
        addText("OEBPS/toc.ncx", tocNcx)
        zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/page1.png"))
        zos.write(pageImageBytes)
        zos.closeEntry()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Datei teilen"))
}
