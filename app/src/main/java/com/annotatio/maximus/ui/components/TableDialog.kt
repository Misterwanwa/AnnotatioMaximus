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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annotatio.maximus.model.Annotation

private const val MAX_COLS = 8
private const val MAX_ROWS = 8

/**
 * Step 1: Matrix picker (up to 8×8) where the user hovers/taps to select rows & cols.
 */
@Composable
fun TablePickerDialog(
    onDismiss: () -> Unit,
    onSizeSelected: (rows: Int, cols: Int) -> Unit
) {
    var hoveredRow by remember { mutableStateOf(0) }
    var hoveredCol by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabellengröße wählen") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hoveredRow > 0 && hoveredCol > 0)
                        "${hoveredRow} × ${hoveredCol}"
                    else
                        "Tippe auf eine Zelle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 1..MAX_ROWS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 1..MAX_COLS) {
                                val isHighlighted = row <= hoveredRow && col <= hoveredCol
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(
                                            width = 1.dp,
                                            color = if (isHighlighted)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                        .background(
                                            if (isHighlighted)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                Color.Transparent
                                        )
                                        .clickable {
                                            hoveredRow = row
                                            hoveredCol = col
                                            onSizeSelected(row, col)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Step 2: Table editor — shows a grid of text fields, one per cell.
 * The user can edit each cell and confirm to insert the table annotation.
 */
@Composable
fun TableEditorDialog(
    rows: Int,
    cols: Int,
    initialCells: List<List<String>>? = null,
    onDismiss: () -> Unit,
    onConfirm: (cells: List<List<String>>) -> Unit
) {
    val cells = remember {
        mutableStateOf(
            initialCells ?: List(rows) { List(cols) { "" } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabelle bearbeiten (${rows}×${cols})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until cols) {
                            val text = cells.value[row][col]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = { newText ->
                                        val updated = cells.value.toMutableList().map { it.toMutableList() }
                                        updated[row][col] = newText
                                        cells.value = updated
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cells.value) }) {
                Text("Einfügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
