package com.annotatio.maximus.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SearchResult(val page: Int, val snippet: String)

private suspend fun searchPdfText(
    context: Context,
    pdfUri: Uri,
    query: String
): List<SearchResult> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val results = mutableListOf<SearchResult>()
    try {
        // Copy URI to a temporary file so PDFBox can read it
        val tmpFile = File(context.cacheDir, "search_tmp.pdf")
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
        }
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(tmpFile)
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        val pageCount = doc.numberOfPages
        for (i in 0 until pageCount) {
            stripper.startPage = i + 1
            stripper.endPage = i + 1
            val text = stripper.getText(doc)
            val lower = text.lowercase()
            val queryLower = query.lowercase()
            if (lower.contains(queryLower)) {
                // Build a short snippet around the first occurrence
                val idx = lower.indexOf(queryLower)
                val start = (idx - 60).coerceAtLeast(0)
                val end = (idx + query.length + 60).coerceAtMost(text.length)
                val snippet = text.substring(start, end).replace('\n', ' ').trim()
                results.add(SearchResult(page = i, snippet = snippet))
            }
        }
        doc.close()
    } catch (_: Exception) { /* ignore */ }
    results
}

/**
 * Dialog for full-text search across all pages of the open PDF.
 * Uses PDFBox for text extraction.
 */
@Composable
fun SearchDialog(
    pdfUri: Uri?,
    onNavigateToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Suchen")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Suchbegriff") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Suchen",
                                modifier = Modifier.clickable {
                                    if (pdfUri == null || query.isBlank()) return@clickable
                                    scope.launch {
                                        isSearching = true
                                        hasSearched = false
                                        results = searchPdfText(context, pdfUri, query)
                                        isSearching = false
                                        hasSearched = true
                                    }
                                }
                            )
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                if (pdfUri == null) {
                    Text(
                        "Kein PDF geöffnet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (hasSearched) {
                    if (results.isEmpty()) {
                        Text(
                            "Keine Ergebnisse für „$query".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${results.size} Treffer gefunden:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                            items(results) { result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNavigateToPage(result.page)
                                            onDismiss()
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        "Seite ${result.page + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val snippetAnnotated = buildAnnotatedString {
                                        val lowerSnippet = result.snippet.lowercase()
                                        val lowerQuery = query.lowercase()
                                        var start = 0
                                        while (true) {
                                            val idx = lowerSnippet.indexOf(lowerQuery, start)
                                            if (idx == -1) {
                                                append(result.snippet.substring(start))
                                                break
                                            }
                                            append(result.snippet.substring(start, idx))
                                            withStyle(SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )) {
                                                append(result.snippet.substring(idx, idx + query.length))
                                            }
                                            start = idx + query.length
                                        }
                                    }
                                    Text(
                                        snippetAnnotated,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pdfUri == null || query.isBlank()) return@TextButton
                    scope.launch {
                        isSearching = true
                        hasSearched = false
                        results = searchPdfText(context, pdfUri, query)
                        isSearching = false
                        hasSearched = true
                    }
                },
                enabled = !isSearching && pdfUri != null && query.isNotBlank()
            ) { Text("Suchen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
