package com.annotatio.maximus.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp

data class Bookmark(
    val pageIndex: Int,
    val label: String
)

private const val PREFS_BOOKMARKS = "bookmarks_prefs"

fun loadBookmarks(context: Context, pdfUriString: String): List<Bookmark> {
    val prefs = context.getSharedPreferences(PREFS_BOOKMARKS, Context.MODE_PRIVATE)
    val key = "bookmarks_${pdfUriString.hashCode()}"
    val raw = prefs.getString(key, "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val parts = entry.split(":", limit = 2)
        if (parts.size == 2) Bookmark(parts[0].toIntOrNull() ?: return@mapNotNull null, parts[1])
        else null
    }
}

fun saveBookmarks(context: Context, pdfUriString: String, bookmarks: List<Bookmark>) {
    val prefs = context.getSharedPreferences(PREFS_BOOKMARKS, Context.MODE_PRIVATE)
    val key = "bookmarks_${pdfUriString.hashCode()}"
    val raw = bookmarks.joinToString("|") { "${it.pageIndex}:${it.label}" }
    prefs.edit().putString(key, raw).apply()
}

/**
 * Panel showing all bookmarks for the current document.
 * Shown as a dialog.
 */
@Composable
fun BookmarkPanel(
    bookmarks: List<Bookmark>,
    currentPage: Int,
    onNavigate: (Int) -> Unit,
    onAdd: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onRename: (Bookmark, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("Seite ${currentPage + 1}") }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editLabel by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Lesezeichen hinzufügen") },
            text = {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Bezeichnung") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newLabel.isNotBlank()) {
                        onAdd(Bookmark(currentPage, newLabel.trim()))
                    }
                    showAddDialog = false
                    newLabel = "Seite ${currentPage + 1}"
                }) { Text("Hinzufügen") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    val editBm = editingBookmark
    if (editBm != null) {
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("Lesezeichen umbenennen") },
            text = {
                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it },
                    label = { Text("Bezeichnung") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editLabel.isNotBlank()) onRename(editBm, editLabel.trim())
                    editingBookmark = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text("Abbrechen") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lesezeichen") },
        text = {
            Column {
                TextButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aktuelle Seite als Lesezeichen speichern")
                }
                Divider()
                Spacer(Modifier.height(4.dp))
                if (bookmarks.isEmpty()) {
                    Text(
                        "Noch keine Lesezeichen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        bookmarks.forEach { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(bm.pageIndex); onDismiss() }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(bm.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Seite ${bm.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    editLabel = bm.label
                                    editingBookmark = bm
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Umbenennen", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(bm) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
