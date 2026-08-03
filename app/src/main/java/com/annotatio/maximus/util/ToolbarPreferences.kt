package com.annotatio.maximus.util

import android.content.Context
import com.annotatio.maximus.ui.theme.AppTheme

private const val PREFS_NAME = "toolbar_prefs"
private const val PREFIX = "toolbar_show_"

private val DEFAULT_VISIBILITY = mapOf(
    "open" to true,
    "hand" to true,
    "pen" to true,
    "marker" to true,
    "underline" to true,
    "strikethrough" to true,
    "table" to true,
    "note" to true,
    "textbox" to true,
    "comment" to true,
    "eraser" to true,
    "shapes" to true,
    "signature" to true,
    "image" to true,
    "link" to true,
    "select" to true,
    "lasso" to true,
    "smartgraphic" to true,
    "translator" to true,
    "converter" to true,
    "scanner" to true,
    "search" to true,
    "tts" to true,
    "wifi" to true,
    "cloud" to true,
    "createpdf" to true,
    "password" to true,
    "gemini" to true,
    "undo" to true,
    "redo" to true,
    "save" to true
)

fun loadToolbarVisibility(context: Context): Map<String, Boolean> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return DEFAULT_VISIBILITY.keys.associateWith { id ->
        prefs.getBoolean(PREFIX + id, DEFAULT_VISIBILITY[id]!!)
    }
}

fun saveToolbarVisibility(context: Context, visibility: Map<String, Boolean>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        visibility.forEach { (id, value) ->
            putBoolean(PREFIX + id, value)
        }
        apply()
    }
}

private const val KEY_APP_THEME = "app_theme"

fun loadAppTheme(context: Context): AppTheme {
    val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_APP_THEME, AppTheme.CUPERTINO_MINIMALIST.name)
        ?: AppTheme.CUPERTINO_MINIMALIST.name
    return try { AppTheme.valueOf(name) } catch (_: IllegalArgumentException) { AppTheme.CUPERTINO_MINIMALIST }
}

fun saveAppTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_APP_THEME, theme.name)
        .apply()
}
