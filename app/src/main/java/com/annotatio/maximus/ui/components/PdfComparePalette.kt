package com.annotatio.maximus.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class CompareMode(val label: String) {
    SIDE_BY_SIDE("Side-by-Side"),
    OVERLAY_DIFF("Differenz-Overlay (Rot/Grün)"),
    SPLIT_SLIDER("Wisch-Schieberegler")
}

@Composable
fun PdfComparePalette(
    currentPdfUri: Uri?,
    revisionBUri: Uri?,
    activeMode: CompareMode,
    isSyncZoomEnabled: Boolean,
    onSelectRevisionB: (Uri) -> Unit,
    onModeChanged: (CompareMode) -> Unit,
    onToggleSyncZoom: (Boolean) -> Unit,
    onStartCompare: () -> Unit,
    onDismiss: () -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectRevisionB(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Difference, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Revisionsvergleich (PDF Compare)")
            }
        },
        text = {
            Column {
                Text(
                    text = "Vergleiche zwei Planungsstände visuell, um Änderungen, Korrekturen oder gelöschte Elemente sofort zu erkennen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                // File Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Revision A (Basis):", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = currentPdfUri?.lastPathSegment ?: "Aktuelles Dokument",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Revision B (Vergleichsstand):", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = revisionBUri?.lastPathSegment ?: "Noch keine Datei gewählt",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (revisionBUri == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Revision B auswählen…")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Vergleichsmodus:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompareMode.values().forEach { mode ->
                        FilterChip(
                            selected = activeMode == mode,
                            onClick = { onModeChanged(mode) },
                            leadingIcon = {
                                when (mode) {
                                    CompareMode.SIDE_BY_SIDE -> Icon(Icons.Default.Splitscreen, contentDescription = null)
                                    CompareMode.OVERLAY_DIFF -> Icon(Icons.Default.Compare, contentDescription = null)
                                    CompareMode.SPLIT_SLIDER -> Icon(Icons.Default.Tune, contentDescription = null)
                                }
                            },
                            label = { Text(mode.label) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Synchrones Zoomen & Scrollen", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isSyncZoomEnabled,
                        onCheckedChange = onToggleSyncZoom
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartCompare()
                    onDismiss()
                },
                enabled = revisionBUri != null
            ) {
                Text("Vergleich starten")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}
