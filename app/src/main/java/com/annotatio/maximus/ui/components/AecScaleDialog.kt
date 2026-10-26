package com.annotatio.maximus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.util.MeasureCalculator

@Composable
fun AecScaleDialog(
    currentScale: DrawingScale,
    onScaleChanged: (DrawingScale) -> Unit,
    onDismiss: () -> Unit
) {
    var rawInput by remember { mutableStateOf(currentScale.label) }
    var selectedUnit by remember { mutableStateOf(currentScale.unit) }

    val presets = listOf("1:20", "1:50", "1:100", "1:200", "1:500")
    val units = listOf("m", "cm", "mm", "ft")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maßstab & Kalibrierung", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "Wähle einen Plan-Maßstab oder gib ein Quick-Scale-Kürzel ein (z. B. '12.3m' für eine Referenzlinie).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Voreinstellungen:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = rawInput == preset,
                            onClick = {
                                rawInput = preset
                                val ratio = preset.removePrefix("1:").toFloatOrNull() ?: 100f
                                onScaleChanged(
                                    currentScale.copy(
                                        ratio = ratio,
                                        label = preset,
                                        pixelsPerUnit = (72f * 100f / ratio).coerceAtLeast(1f)
                                    )
                                )
                                onDismiss()
                            },
                            label = { Text(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Einheit:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    units.forEach { u ->
                        FilterChip(
                            selected = selectedUnit == u,
                            onClick = {
                                selectedUnit = u
                                onScaleChanged(currentScale.copy(unit = u))
                            },
                            label = { Text(u) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Benutzerdefinierter Maßstab (z. B. 1:75 oder 5.0m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = MeasureCalculator.calibrateScale(
                        pixelLength = 100f,
                        rawInput = rawInput,
                        currentScale = currentScale.copy(unit = selectedUnit)
                    )
                    onScaleChanged(updated)
                    onDismiss()
                }
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
