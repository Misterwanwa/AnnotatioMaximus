package com.annotatio.maximus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val CupertinoColorScheme = lightColorScheme(
    primary              = Primary,
    onPrimary            = OnPrimary,
    primaryContainer     = PrimaryContainer,
    onPrimaryContainer   = OnPrimaryContainer,
    surface              = Surface,
    onSurface            = OnSurface,
    surfaceVariant       = SurfaceVariant,
    onSurfaceVariant     = OnSurfaceVariant,
    secondary            = CupertinoAccent,
    onSecondary          = OnPrimary
)

private val FrutigerAeroColorScheme = lightColorScheme(
    primary              = AeroPrimary,
    onPrimary            = AeroOnPrimary,
    primaryContainer     = AeroPrimaryContainer,
    onPrimaryContainer   = AeroOnPrimaryContainer,
    surface              = AeroSurface,
    onSurface            = AeroOnSurface,
    surfaceVariant       = AeroSurfaceVariant,
    onSurfaceVariant     = AeroOnSurfaceVariant,
    secondary            = AeroOliveGreen,
    onSecondary          = AeroOnPrimary,
    tertiary             = AeroSilver,
    onTertiary           = AeroOnSurface
)

private val LonghornSlateColorScheme = darkColorScheme(
    primary              = SlatePrimary,
    onPrimary            = SlateOnPrimary,
    primaryContainer     = SlatePrimaryContainer,
    onPrimaryContainer   = SlateOnPrimaryContainer,
    surface              = SlateSurface,
    onSurface            = SlateOnSurface,
    surfaceVariant       = SlateSurfaceVariant,
    onSurfaceVariant     = SlateOnSurfaceVariant,
    secondary            = SlatePrimary,
    onSecondary          = SlateOnPrimary
)

@Composable
fun AnnotatioMaximusTheme(
    appTheme: AppTheme = AppTheme.CUPERTINO_MINIMALIST,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CUPERTINO_MINIMALIST -> CupertinoColorScheme
        AppTheme.FRUTIGER_AERO        -> FrutigerAeroColorScheme
        AppTheme.LONGHORN_SLATE       -> LonghornSlateColorScheme
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
