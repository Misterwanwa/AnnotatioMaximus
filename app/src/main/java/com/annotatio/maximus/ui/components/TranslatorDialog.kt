package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---------- Language registry --------------------------------------------------

data class TranslateLanguage(val code: String, val label: String)

val TRANSLATE_LANGUAGES = listOf(
    TranslateLanguage("de", "Deutsch"),
    TranslateLanguage("en", "Englisch"),
    TranslateLanguage("fr", "Französisch"),
    TranslateLanguage("es", "Spanisch"),
    TranslateLanguage("it", "Italienisch"),
    TranslateLanguage("pt", "Portugiesisch"),
    TranslateLanguage("nl", "Niederländisch"),
    TranslateLanguage("pl", "Polnisch"),
    TranslateLanguage("ru", "Russisch"),
    TranslateLanguage("zh", "Chinesisch (vereinfacht)"),
    TranslateLanguage("ja", "Japanisch"),
    TranslateLanguage("ko", "Koreanisch"),
    TranslateLanguage("ar", "Arabisch"),
    TranslateLanguage("tr", "Türkisch"),
    TranslateLanguage("sv", "Schwedisch"),
    TranslateLanguage("da", "Dänisch"),
    TranslateLanguage("fi", "Finnisch"),
    TranslateLanguage("no", "Norwegisch"),
    TranslateLanguage("cs", "Tschechisch"),
    TranslateLanguage("hu", "Ungarisch")
)

private const val PREFS_TRANSLATOR = "translator_prefs"
private const val KEY_TARGET_LANG = "target_language"

fun loadTargetLanguage(context: Context): String =
    context.getSharedPreferences(PREFS_TRANSLATOR, Context.MODE_PRIVATE)
        .getString(KEY_TARGET_LANG, "de") ?: "de"

fun saveTargetLanguage(context: Context, code: String) {
    context.getSharedPreferences(PREFS_TRANSLATOR, Context.MODE_PRIVATE)
        .edit().putString(KEY_TARGET_LANG, code).apply()
}

// ---------- Translate via Google Translate browser link -----------------------

fun openGoogleTranslate(context: Context, text: String, targetLangCode: String) {
    val encoded = Uri.encode(text)
    val url = "https://translate.google.com/?sl=auto&tl=$targetLangCode&text=$encoded&op=translate"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

// ---------- Main translator dialog -------------------------------------------

/**
 * Shows the text-input translator dialog.
 * The user can paste/type text and tap "Übersetzen" to open Google Translate in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorDialog(
    initialText: String = "",
    targetLangCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instant-Übersetzer") },
        text = {
            Column {
                Text(
                    "Zielsprache: ${TRANSLATE_LANGUAGES.find { it.code == targetLangCode }?.label ?: targetLangCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Text zum Übersetzen eingeben…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tipp: Öffnet Google Übersetzer im Browser",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        openGoogleTranslate(context, inputText.trim(), targetLangCode)
                    }
                    onDismiss()
                }
            ) { Text("Übersetzen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// ---------- Language picker for Settings ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerRow(
    currentCode: String,
    onLanguageChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = TRANSLATE_LANGUAGES.find { it.code == currentCode }
        ?: TRANSLATE_LANGUAGES.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Übersetzungssprache") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TRANSLATE_LANGUAGES.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    onClick = {
                        onLanguageChanged(lang.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
