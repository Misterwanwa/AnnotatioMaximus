package com.annotatio.maximus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.util.MeasureCalculator

@Composable
fun AecTakeoffDialog(
    items: List<MeasurementItem>,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aufmaß-Stückliste (Takeoff)", style = MaterialTheme.typography.titleLarge)
                if (items.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(Icons.Default.Delete, contentDescription = "Alles leeren", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "Übersicht aller gemessenen Strecken, Flächen und Zählobjekte auf dem Plan:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (items.isEmpty()) {
                    Text(
                        text = "Noch keine Messungen vorgenommen. Wähle ein Messwerkzeug (Strecke, Fläche oder Zähler), um Mengen zu erfassen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label.ifBlank { item.type.name },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Seite ${item.pageIndex + 1} • ${item.formattedText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteItem(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val csv = MeasureCalculator.exportTakeoffToCsv(items)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Aufmaß CSV", csv)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "CSV in die Zwischenablage kopiert!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("CSV kopieren")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("Schließen")
                }
            }
        }
    )
}
