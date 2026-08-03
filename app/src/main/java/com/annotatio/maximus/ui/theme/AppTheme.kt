package com.annotatio.maximus.ui.theme

import androidx.compose.runtime.compositionLocalOf

enum class AppTheme {
    CUPERTINO_MINIMALIST,
    FRUTIGER_AERO,
    LONGHORN_SLATE
}

val LocalAppTheme = compositionLocalOf { AppTheme.CUPERTINO_MINIMALIST }
