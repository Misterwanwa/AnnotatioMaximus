package com.annotatio.maximus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Lasso
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Einträge für die Button-Sichtbarkeit, alphabetisch nach deutschem Label sortiert.
 */
private val BUTTON_ENTRIES = listOf(
    "comment" to Pair("Kommentar", Icons.Default.ModeComment),
    "converter" to Pair("Konverter", Icons.Default.ImportExport),
    "eraser" to Pair("Radierer", Icons.Default.CleaningServices),
    "gemini" to Pair("KI-Bild", Icons.Default.SmartToy),
    "image" to Pair("Bild", Icons.Default.AddPhotoAlternate),
    "lasso" to Pair("Lasso", Icons.Default.Lasso),
    "link" to Pair("Link", Icons.Default.Link),
    "marker" to Pair("Marker", Icons.Default.BorderColor),
    "note" to Pair("Notiz", Icons.Default.StickyNote2),
    "open" to Pair("Ordner", Icons.Default.FolderOpen),
    "pen" to Pair("Stift", Icons.Default.Draw),
    "redo" to Pair("Vorwärts", Icons.Default.Redo),
    "save" to Pair("Speichern", Icons.Default.Save),
    "scanner" to Pair("Scanner", Icons.Default.DocumentScanner),
    "select" to Pair("Auswählen", Icons.Default.NearMe),
    "shapes" to Pair("Formen", Icons.Outlined.Interests),
    "signature" to Pair("Unterschrift", Icons.Default.Gesture),
    "smartgraphic" to Pair("Smarte Grafik", Icons.Default.BubbleChart),
    "strikethrough" to Pair("Durchstreichen", Icons.Default.FormatStrikethrough),
    "table" to Pair("Tabelle", Icons.Default.TableChart),
    "translator" to Pair("Übersetzer", Icons.Default.GTranslate),
    "underline" to Pair("Unterstreichen", Icons.Default.FormatUnderlined),
    "undo" to Pair("Zurück", Icons.Default.Undo)
)

@Composable
fun SettingsDialog(
    toolbarVisibility: Map<String, Boolean>,
    onToolbarVisibilityChange: (Map<String, Boolean>) -> Unit,
    translatorLanguageCode: String = "de",
    onTranslatorLanguageChange: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var showButtonsSettings by remember { mutableStateOf(false) }

    if (showButtonsSettings) {
        ButtonsSettingsDialog(
            visibility = toolbarVisibility,
            onVisibilityChange = onToolbarVisibilityChange,
            onDismiss = { showButtonsSettings = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Einstellungen") },
            text = {
                Column {
                    // Translator language
                    LanguagePickerRow(
                        currentCode = translatorLanguageCode,
                        onLanguageChanged = onTranslatorLanguageChange
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showButtonsSettings = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Buttons anpassen",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* TODO: Handle page delete */ }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seiten löschen",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* TODO: Handle page reorder */ }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seiten umordnen",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Schließen")
                }
            }
        )
    }
}

@Composable
private fun ButtonsSettingsDialog(
    visibility: Map<String, Boolean>,
    onVisibilityChange: (Map<String, Boolean>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentVisibility by remember(visibility) { mutableStateOf(visibility.toMap()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buttons") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                BUTTON_ENTRIES.forEach { (id, labelAndIcon) ->
                    val (label, icon) = labelAndIcon
                    val isVisible = currentVisibility[id] ?: true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).padding(start = 8.dp)
                            )
                        }
                        Switch(
                            checked = isVisible,
                            onCheckedChange = { checked ->
                                val newMap = currentVisibility.toMutableMap().apply { put(id, checked) }
                                currentVisibility = newMap
                                onVisibilityChange(newMap)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fertig")
            }
        }
    )
}
