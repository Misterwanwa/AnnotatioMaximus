package com.annotatio.maximus.ui.components

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.ServerSocket

/**
 * Minimal single-file HTTP server that serves the current PDF over Wi-Fi.
 * The dialog shows the URL the user can open on any browser on the same network.
 */
@Composable
fun WifiShareDialog(
    pdfUri: Uri?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Starte Server…") }
    var serverSocket by remember { mutableStateOf<ServerSocket?>(null) }

    // Start HTTP server on a background thread
    DisposableEffect(pdfUri) {
        if (pdfUri == null) {
            statusText = "Keine Datei geöffnet."
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    val socket = ServerSocket(0) // random free port
                    serverSocket = socket
                    val port = socket.localPort
                    val ip = getWifiIpAddress(context)
                    withContext(Dispatchers.Main) {
                        serverUrl = "http://$ip:$port/document.pdf"
                        statusText = "Server läuft"
                    }
                    // Accept connections in a loop until the socket is closed
                    while (!socket.isClosed) {
                        try {
                            val client = socket.accept()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val input = client.getInputStream()
                                    val requestLine = input.bufferedReader().readLine() ?: ""
                                    // Ignore request details; always serve the PDF
                                    val pdfBytes = context.contentResolver
                                        .openInputStream(pdfUri)?.readBytes()
                                    val out: OutputStream = client.getOutputStream()
                                    if (pdfBytes != null) {
                                        val header = "HTTP/1.1 200 OK\r\n" +
                                                "Content-Type: application/pdf\r\n" +
                                                "Content-Length: ${pdfBytes.size}\r\n" +
                                                "Content-Disposition: attachment; filename=\"document.pdf\"\r\n" +
                                                "\r\n"
                                        out.write(header.toByteArray())
                                        out.write(pdfBytes)
                                    } else {
                                        out.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                                    }
                                    out.flush()
                                } finally {
                                    client.close()
                                }
                            }
                        } catch (_: Exception) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText = "Fehler: ${e.message}"
                    }
                }
            }
        }
        onDispose {
            serverSocket?.close()
            serverSocket = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            serverSocket?.close()
            onDismiss()
        },
        title = { Text("WLAN-Freigabe") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (serverUrl != null) {
                    Text(
                        "Öffne diese Adresse im Browser eines Geräts im gleichen WLAN:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        serverUrl!!,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Der Server läuft so lange dieser Dialog geöffnet ist.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Verbinde mit WLAN, um die Freigabe zu starten.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { serverSocket?.close(); onDismiss() }) { Text("Schließen") }
        }
    )
}

private fun getWifiIpAddress(context: Context): String {
    return try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    } catch (_: Exception) {
        "0.0.0.0"
    }
}
