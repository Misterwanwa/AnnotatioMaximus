package com.annotatio.maximus.ui.components

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Extracts visible text from a rendered PDF page using PdfRenderer.
 * For a lightweight approximation: we use PdfRenderer to get the page
 * and rely on PDFBox text extraction if available; otherwise render a
 * bitmap and use the page content streams via PdfRenderer's built-in
 * text search API where available (API 35+).
 * For API < 35 we return a placeholder.
 */
private suspend fun extractPageText(context: Context, pdfFile: File, pageIndex: Int): String =
    withContext(Dispatchers.IO) {
        return@withContext try {
            // Try PDFBox text extraction
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFile)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val text = stripper.getText(doc)
            doc.close()
            text.ifBlank { "Kein extrahierbarer Text auf dieser Seite." }
        } catch (_: Exception) {
            "Text konnte nicht extrahiert werden."
        }
    }

/**
 * Dialog for reading the current PDF page aloud via Android TTS.
 */
@Composable
fun TtsDialog(
    pdfFile: File?,
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initialisiere Sprachausgabe…") }
    var speechRate by remember { mutableFloatStateOf(1f) }
    var currentText by remember { mutableStateOf("") }
    var readingPage by remember { mutableIntStateOf(currentPage) }

    // Init TTS
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            statusText = if (ttsReady) "Bereit zum Vorlesen" else "TTS nicht verfügbar"
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ttsReady) return
        engine.setSpeechRate(speechRate)
        engine.language = Locale.getDefault()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true; isPaused = false }
            override fun onDone(utteranceId: String?) { isSpeaking = false; isPaused = false; statusText = "Fertig" }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { isSpeaking = false; statusText = "Fehler bei der Sprachausgabe" }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_read")
        isSpeaking = true
    }

    fun loadAndSpeak(page: Int) {
        val file = pdfFile ?: run { statusText = "Keine PDF-Datei geladen"; return }
        scope.launch {
            isLoading = true
            statusText = "Extrahiere Text von Seite ${page + 1}…"
            val text = extractPageText(context, file, page)
            currentText = text
            isLoading = false
            statusText = "Lese Seite ${page + 1} vor…"
            speak(text)
        }
    }

    AlertDialog(
        onDismissRequest = {
            tts?.stop()
            onDismiss()
        },
        title = { Text("PDF vorlesen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Page indicator
                Text(
                    "Seite ${readingPage + 1} von $pageCount",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(8.dp))

                // Playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous page
                    IconButton(
                        onClick = {
                            if (readingPage > 0) {
                                readingPage--
                                loadAndSpeak(readingPage)
                            }
                        },
                        enabled = ttsReady && readingPage > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Vorherige Seite")
                    }

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else {
                        // Play / Pause
                        IconButton(
                            onClick = {
                                val engine = tts
                                if (engine == null || !ttsReady) return@IconButton
                                when {
                                    isSpeaking && !isPaused -> {
                                        engine.stop()
                                        isSpeaking = false
                                        isPaused = true
                                        statusText = "Pausiert"
                                    }
                                    isPaused -> {
                                        speak(currentText)
                                    }
                                    else -> {
                                        loadAndSpeak(readingPage)
                                    }
                                }
                            },
                            enabled = ttsReady && pdfFile != null
                        ) {
                            Icon(
                                if (isSpeaking && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isSpeaking) "Pause" else "Abspielen",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Stop
                    IconButton(
                        onClick = {
                            tts?.stop()
                            isSpeaking = false
                            isPaused = false
                            statusText = "Gestoppt"
                        },
                        enabled = isSpeaking || isPaused
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stopp")
                    }

                    // Next page
                    IconButton(
                        onClick = {
                            if (readingPage < pageCount - 1) {
                                readingPage++
                                loadAndSpeak(readingPage)
                            }
                        },
                        enabled = ttsReady && readingPage < pageCount - 1
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Nächste Seite")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Speed control
                Text(
                    "Geschwindigkeit: ${String.format("%.1f", speechRate)}x",
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        tts?.setSpeechRate(it)
                    },
                    valueRange = 0.5f..2.5f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { tts?.stop(); onDismiss() }) { Text("Schließen") }
        }
    )
}
