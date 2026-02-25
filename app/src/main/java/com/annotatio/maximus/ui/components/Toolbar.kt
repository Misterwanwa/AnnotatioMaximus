package com.annotatio.maximus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Lasso
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.AnnotationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationToolbar(
    activeTool: AnnotationType?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasDocument: Boolean,
    toolbarVisibility: Map<String, Boolean>,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit,
    onToolSelected: (AnnotationType?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpenGeminiSketch: () -> Unit,
    onOpenConverter: () -> Unit = {},
    onOpenTranslator: () -> Unit = {},
    onSmartGraphicSelected: (com.annotatio.maximus.model.SmartGraphicType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val show = { id: String -> toolbarVisibility[id] != false }

    TopAppBar(
        title = {
            Text(
                text = "Annotatio Maximus",
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            // Ordner
            if (show("open")) {
                IconButton(onClick = onOpenFile) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "PDF öffnen")
                }
            }

            // Einstellungen
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
            }

            if (show("pen") || show("marker") || show("underline") || show("strikethrough") || show("table") || show("note") || show("comment") || show("eraser") || show("shapes") || show("signature") || show("image") || show("link") || show("select") || show("lasso") || show("smartgraphic") || show("translator") || show("converter") || show("gemini")) {
                ToolbarDivider()
            }

            // Stift
            if (show("pen")) {
                ToolToggleButton(
                    icon = Icons.Default.Draw,
                    label = "Stift",
                    isActive = activeTool == AnnotationType.PEN,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.PEN) null else AnnotationType.PEN
                        )
                    }
                )
            }

            if (show("marker")) {
                ToolToggleButton(
                    icon = Icons.Default.BorderColor,
                    label = "Marker",
                    isActive = activeTool == AnnotationType.HIGHLIGHTER,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.HIGHLIGHTER) null
                            else AnnotationType.HIGHLIGHTER
                        )
                    }
                )
            }

            if (show("underline")) {
                ToolToggleButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Unterstreichen",
                    isActive = activeTool == AnnotationType.UNDERLINE,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.UNDERLINE) null
                            else AnnotationType.UNDERLINE
                        )
                    }
                )
            }

            if (show("strikethrough")) {
                ToolToggleButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Durchstreichen",
                    isActive = activeTool == AnnotationType.STRIKETHROUGH,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.STRIKETHROUGH) null
                            else AnnotationType.STRIKETHROUGH
                        )
                    }
                )
            }

            if (show("table")) {
                ToolToggleButton(
                    icon = Icons.Default.TableChart,
                    label = "Tabelle",
                    isActive = activeTool == AnnotationType.TABLE,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.TABLE) null
                            else AnnotationType.TABLE
                        )
                    }
                )
            }

            if (show("note")) {
                ToolToggleButton(
                    icon = Icons.Default.StickyNote2,
                    label = "Notiz",
                    isActive = activeTool == AnnotationType.TEXT_NOTE,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.TEXT_NOTE) null
                            else AnnotationType.TEXT_NOTE
                        )
                    }
                )
            }

            if (show("comment")) {
                ToolToggleButton(
                    icon = Icons.Default.ModeComment,
                    label = "Kommentar",
                    isActive = activeTool == AnnotationType.COMMENT,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.COMMENT) null
                            else AnnotationType.COMMENT
                        )
                    }
                )
            }

            if (show("eraser")) {
                ToolToggleButton(
                    icon = Icons.Default.CleaningServices,
                    label = "Radierer",
                    isActive = activeTool == AnnotationType.ERASER,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.ERASER) null
                            else AnnotationType.ERASER
                        )
                    }
                )
            }

            // Formen (Dropdown)
            val shapeTools = listOf(
                AnnotationType.CIRCLE,
                AnnotationType.SQUARE,
                AnnotationType.RECTANGLE,
                AnnotationType.TRIANGLE
            )
            val isShapeActive = activeTool in shapeTools
            if (show("shapes")) {
                var showShapeMenu by remember { mutableStateOf(false) }
                Box {
                    ToolToggleButton(
                        icon = Icons.Outlined.Interests,
                        label = "Formen",
                        isActive = isShapeActive,
                        enabled = hasDocument,
                        onClick = { showShapeMenu = true }
                    )
                    DropdownMenu(
                        expanded = showShapeMenu,
                        onDismissRequest = { showShapeMenu = false }
                    ) {
                        listOf(
                            AnnotationType.CIRCLE to "Kreis",
                            AnnotationType.SQUARE to "Quadrat",
                            AnnotationType.RECTANGLE to "Rechteck",
                            AnnotationType.TRIANGLE to "Dreieck"
                        ).forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showShapeMenu = false
                                    onToolSelected(if (activeTool == type) null else type)
                                }
                            )
                        }
                    }
                }
            }

            if (show("signature")) {
                ToolToggleButton(
                    icon = Icons.Default.Gesture,
                    label = "Unterschrift",
                    isActive = activeTool == AnnotationType.SIGNATURE,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.SIGNATURE) null else AnnotationType.SIGNATURE
                        )
                    }
                )
            }

            if (show("image")) {
                ToolToggleButton(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = "Bild einfügen",
                    isActive = activeTool == AnnotationType.IMAGE,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.IMAGE) null else AnnotationType.IMAGE
                        )
                    }
                )
            }

            if (show("link")) {
                ToolToggleButton(
                    icon = Icons.Default.Link,
                    label = "Link",
                    isActive = activeTool == AnnotationType.LINK,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.LINK) null else AnnotationType.LINK
                        )
                    }
                )
            }

            if (show("select")) {
                ToolToggleButton(
                    icon = Icons.Default.NearMe,
                    label = "Auswählen",
                    isActive = activeTool == AnnotationType.SELECT,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.SELECT) null else AnnotationType.SELECT
                        )
                    }
                )
            }

            if (show("lasso")) {
                ToolToggleButton(
                    icon = Icons.Default.Lasso,
                    label = "Lasso",
                    isActive = activeTool == AnnotationType.LASSO,
                    enabled = hasDocument,
                    onClick = {
                        onToolSelected(
                            if (activeTool == AnnotationType.LASSO) null else AnnotationType.LASSO
                        )
                    }
                )
            }

            // Smarte Grafiken (Dropdown)
            if (show("smartgraphic")) {
                var showSmartMenu by remember { mutableStateOf(false) }
                Box {
                    ToolToggleButton(
                        icon = Icons.Default.BubbleChart,
                        label = "Smarte Grafik",
                        isActive = activeTool == AnnotationType.SMART_GRAPHIC,
                        enabled = hasDocument,
                        onClick = { showSmartMenu = true }
                    )
                    DropdownMenu(
                        expanded = showSmartMenu,
                        onDismissRequest = { showSmartMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BubbleChart, contentDescription = null,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MindMap")
                                }
                            },
                            onClick = {
                                showSmartMenu = false
                                onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP)
                                onToolSelected(AnnotationType.SMART_GRAPHIC)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountTree, contentDescription = null,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Organigramm")
                                }
                            },
                            onClick = {
                                showSmartMenu = false
                                onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.ORG_CHART)
                                onToolSelected(AnnotationType.SMART_GRAPHIC)
                            }
                        )
                    }
                }
            }

            // Übersetzer
            if (show("translator")) {
                IconButton(
                    onClick = onOpenTranslator,
                    enabled = hasDocument
                ) {
                    Icon(Icons.Default.GTranslate, contentDescription = "Übersetzer")
                }
            }

            // Konverter
            if (show("converter")) {
                IconButton(
                    onClick = onOpenConverter,
                    enabled = hasDocument
                ) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Konvertieren")
                }
            }

            if (show("gemini")) {
                IconButton(onClick = onOpenGeminiSketch) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Freihand-Skizze für Gemini"
                    )
                }
            }

            if (show("undo") || show("redo")) {
                ToolbarDivider()
            }

            if (show("undo")) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Zurück",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
            if (show("redo")) {
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "Vorwärts",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }

            if (show("save")) {
                ToolbarDivider()
            }

            if (show("save")) {
                IconButton(onClick = onSave, enabled = hasDocument) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Speichern",
                        tint = if (hasDocument) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@Composable
private fun ToolToggleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(modifier = Modifier.width(4.dp))
    Divider(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
    Spacer(modifier = Modifier.width(4.dp))
}
