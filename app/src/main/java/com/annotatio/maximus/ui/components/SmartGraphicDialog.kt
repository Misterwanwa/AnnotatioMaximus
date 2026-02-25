package com.annotatio.maximus.ui.components

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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.SmartGraphicType

@Composable
fun SmartGraphicPickerDialog(
    onDismiss: () -> Unit,
    onTypeSelected: (SmartGraphicType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smarte Grafik") },
        text = {
            Column {
                Text(
                    text = "Grafik-Typ auswählen, dann auf die PDF tippen und ziehen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                SmartGraphicEntry(
                    icon = Icons.Default.BubbleChart,
                    label = "MindMap",
                    description = "Zentrale Idee mit verzweigten Themen"
                ) { onTypeSelected(SmartGraphicType.MIND_MAP) }
                Spacer(modifier = Modifier.height(8.dp))
                SmartGraphicEntry(
                    icon = Icons.Default.AccountTree,
                    label = "Organigramm",
                    description = "Hierarchische Struktur / Baumdiagramm"
                ) { onTypeSelected(SmartGraphicType.ORG_CHART) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun SmartGraphicEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
