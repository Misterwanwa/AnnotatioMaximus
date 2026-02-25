package com.annotatio.maximus.util

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "toolbar_prefs"
private const val PREFIX = "toolbar_show_"

private val DEFAULT_VISIBILITY = mapOf(
    "open" to true,
    "pen" to true,
    "marker" to true,
    "underline" to true,
    "strikethrough" to true,
    "table" to true,
    "note" to true,
    "comment" to true,
    "eraser" to true,
    "shapes" to true,
    "signature" to true,
    "image" to true,
    "link" to true,
    "select" to true,
    "lasso" to true,
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
