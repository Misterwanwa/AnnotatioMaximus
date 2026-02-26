package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Presents cloud storage options:
 * - Upload: shares the current PDF to Google Drive / OneDrive / Dropbox
 *   via the Android share sheet (which lists installed cloud apps).
 * - Open from cloud: fires a document picker that includes cloud providers.
 */
@Composable
fun CloudStorageDialog(
    currentPdfUri: Uri?,
    onOpenFromCloud: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud-Speicher") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {

                // ── Upload current PDF ────────────────────────────────────────
                CloudActionRow(
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Aktuelle PDF hochladen",
                    subtitle = "Teile die geöffnete PDF mit Google Drive, OneDrive, Dropbox…",
                    enabled = currentPdfUri != null,
                    onClick = {
                        if (currentPdfUri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, currentPdfUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "PDF hochladen"))
                        }
                        onDismiss()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Open from cloud ───────────────────────────────────────────
                CloudActionRow(
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    title = "PDF aus Cloud öffnen",
                    subtitle = "Öffne eine PDF aus Google Drive, OneDrive, Dropbox oder einem anderen Anbieter",
                    enabled = true,
                    onClick = {
                        onOpenFromCloud()
                        onDismiss()
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Hinweis: Stelle sicher, dass die jeweiligen Cloud-Apps installiert sind.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@Composable
private fun CloudActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
