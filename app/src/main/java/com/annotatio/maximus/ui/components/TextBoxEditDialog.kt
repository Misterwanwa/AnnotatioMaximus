package com.annotatio.maximus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.TextBoxFont

/** Available text colours shown in the colour picker row. */
private val TEXT_COLORS = listOf(
    Color.Black,
    Color(0xFF212121), // near-black
    Color(0xFF1565C0), // blue
    Color(0xFFC62828), // red
    Color(0xFF2E7D32), // green
    Color(0xFFF57F17), // amber
    Color(0xFF6A1B9A), // purple
    Color(0xFFFFFFFF)  // white
)

/** Font size options in sp. */
private val FONT_SIZES = listOf(8f, 10f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f, 72f)

/**
 * Dialog for creating or editing an [Annotation.TextBox].
 *
 * @param initial  Existing annotation to edit, or null to create a new one.
 * @param onDismiss  Called on cancel.
 * @param onConfirm  Called with the final [Annotation.TextBox] when the user saves.
 *                   The caller supplies the [pageIndex] and position (x/y/width/height)
 *                   for new text boxes; for edits the existing position is preserved.
 */
@Composable
fun TextBoxEditDialog(
    initial: Annotation.TextBox?,
    pageIndex: Int,
    posX: Float = 0.1f,
    posY: Float = 0.1f,
    onDismiss: () -> Unit,
    onConfirm: (Annotation.TextBox) -> Unit
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var isBold by remember { mutableStateOf(initial?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(initial?.isItalic ?: false) }
    var isUnderline by remember { mutableStateOf(initial?.isUnderline ?: false) }
    var isStrikethrough by remember { mutableStateOf(initial?.isStrikethrough ?: false) }
    var fontSize by remember { mutableStateOf(initial?.fontSize ?: 14f) }
    var fontFamily by remember { mutableStateOf(initial?.fontFamily ?: TextBoxFont.DEFAULT) }
    var color by remember { mutableStateOf(initial?.color ?: Color.Black) }

    var showSizePicker by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Textfeld bearbeiten" else "Textfeld einfügen") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // ── Formatting toolbar ────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FormatToggleButton(
                        icon = Icons.Default.FormatBold,
                        label = "Fett",
                        active = isBold,
                        onClick = { isBold = !isBold }
                    )
                    FormatToggleButton(
                        icon = Icons.Default.FormatItalic,
                        label = "Kursiv",
                        active = isItalic,
                        onClick = { isItalic = !isItalic }
                    )
                    FormatToggleButton(
                        icon = Icons.Default.FormatUnderlined,
                        label = "Unterstrichen",
                        active = isUnderline,
                        onClick = { isUnderline = !isUnderline }
                    )
                    FormatToggleButton(
                        icon = Icons.Default.FormatStrikethrough,
                        label = "Durchgestrichen",
                        active = isStrikethrough,
                        onClick = { isStrikethrough = !isStrikethrough }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Font-size picker
                    Box {
                        IconButton(onClick = { showSizePicker = true }) {
                            Icon(
                                Icons.Default.FormatSize,
                                contentDescription = "Schriftgröße",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showSizePicker,
                            onDismissRequest = { showSizePicker = false }
                        ) {
                            FONT_SIZES.forEach { size ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${size.toInt()} sp",
                                            fontWeight = if (size == fontSize)
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            else androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        fontSize = size
                                        showSizePicker = false
                                    }
                                )
                            }
                        }
                    }

                    // Font family picker
                    Box {
                        TextButton(onClick = { showFontPicker = true }) {
                            Text(
                                fontFamily.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = showFontPicker,
                            onDismissRequest = { showFontPicker = false }
                        ) {
                            TextBoxFont.entries.forEach { font ->
                                DropdownMenuItem(
                                    text = { Text(font.label) },
                                    onClick = {
                                        fontFamily = font
                                        showFontPicker = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Colour row ────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.FormatColorText,
                        contentDescription = "Schriftfarbe",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TEXT_COLORS.forEach { c ->
                        val borderMod = if (c == color)
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(borderMod)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Size indicator ────────────────────────────────────────────
                Text(
                    "Schriftgröße: ${fontSize.toInt()} sp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Text input ────────────────────────────────────────────────
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Text eingeben…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 10,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = fontSize.sp,
                        fontWeight = if (isBold) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                        fontStyle = if (isItalic) androidx.compose.ui.text.font.FontStyle.Italic
                        else androidx.compose.ui.text.font.FontStyle.Normal,
                        textDecoration = when {
                            isUnderline && isStrikethrough ->
                                androidx.compose.ui.text.style.TextDecoration.combine(
                                    listOf(
                                        androidx.compose.ui.text.style.TextDecoration.Underline,
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    )
                                )
                            isUnderline -> androidx.compose.ui.text.style.TextDecoration.Underline
                            isStrikethrough -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                            else -> androidx.compose.ui.text.style.TextDecoration.None
                        },
                        color = color
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        val box = if (initial != null) {
                            initial.copy(
                                text = text,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                isStrikethrough = isStrikethrough,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                color = color
                            )
                        } else {
                            Annotation.TextBox(
                                pageIndex = pageIndex,
                                x = posX,
                                y = posY,
                                width = 0.6f,
                                height = 0.15f,
                                text = text,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                isStrikethrough = isStrikethrough,
                                fontSize = fontSize,
                                fontFamily = fontFamily,
                                color = color
                            )
                        }
                        onConfirm(box)
                    }
                    onDismiss()
                }
            ) { Text(if (initial != null) "Speichern" else "Einfügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun FormatToggleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}
