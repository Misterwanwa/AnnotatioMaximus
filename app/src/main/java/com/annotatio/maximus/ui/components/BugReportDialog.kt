package com.annotatio.maximus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BugReportDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var bugDescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Bug melden")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Beschreiben Sie den Bug:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = bugDescription,
                    onValueChange = { bugDescription = it },
                    label = { Text("Bug-Beschreibung") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (bugDescription.isNotBlank()) {
                        onSubmit(bugDescription)
                        onDismiss()
                    }
                },
                enabled = bugDescription.isNotBlank()
            ) {
                Text("Absenden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}