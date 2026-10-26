package com.annotatio.maximus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.util.AndroidMcpServer

@Composable
fun McpServerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(AndroidMcpServer.isRunning) }
    var port by remember { mutableStateOf(AndroidMcpServer.port) }

    val configSnippet = """
{
  "mcpServers": {
    "annotatio-maximus": {
      "url": "http://127.0.0.1:$port"
    }
  }
}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("KI-Schnittstelle (MCP Server)")
            }
        },
        text = {
            Column {
                Text(
                    text = "Das Model Context Protocol (MCP) erlaubt es lokalen KI-Assistenten (wie Claude Desktop), AnnotatioMaximus direkt anzusteuern, Pläne zu analysieren, Texte auszulesen und Bemaßungen abzufragen.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Status & Switch
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (isRunning) Color(0xFF43A047) else Color(0xFFE53935),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(
                                text = if (isRunning) "Server aktiv (Port $port)" else "Server inaktiv",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { start ->
                                if (start) {
                                    port = AndroidMcpServer.start(scope)
                                    isRunning = true
                                } else {
                                    AndroidMcpServer.stop()
                                    isRunning = false
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Verbindung via USB/ADB Port Forwarding:", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "adb forward tcp:$port tcp:$port",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Claude Desktop Konfiguration (claude_desktop_config.json):", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = configSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Claude Config", configSnippet))
                        Toast.makeText(context, "Konfiguration kopiert!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Config kopieren")
                }
                Button(onClick = onDismiss) {
                    Text("Fertig")
                }
            }
        }
    )
}
