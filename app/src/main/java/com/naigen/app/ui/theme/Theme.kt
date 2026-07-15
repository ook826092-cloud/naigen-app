package com.naigen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Colors.L_ACCENT,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Colors.L_BG_SECONDARY,
    onPrimaryContainer = Colors.L_TEXT_PRIMARY,
    secondary = Colors.L_ACCENT,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = Colors.L_BG_PRIMARY,
    onBackground = Colors.L_TEXT_PRIMARY,
    surface = Colors.L_BG_PRIMARY,
    onSurface = Colors.L_TEXT_PRIMARY,
    surfaceVariant = Colors.L_BG_SECONDARY,
    onSurfaceVariant = Colors.L_TEXT_SECONDARY,
    outline = Colors.L_SEPARATOR,
    outlineVariant = Colors.L_SEPARATOR,
    error = Colors.L_DANGER,
    onError = androidx.compose.ui.graphics.Color.White
)

private val DarkColors = darkColorScheme(
    primary = Colors.D_ACCENT,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Colors.D_BG_TERTIARY,
    onPrimaryContainer = Colors.D_TEXT_PRIMARY,
    secondary = Colors.D_ACCENT,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = Colors.D_BG_PRIMARY,
    onBackground = Colors.D_TEXT_PRIMARY,
    surface = Colors.D_BG_PRIMARY,
    onSurface = Colors.D_TEXT_PRIMARY,
    surfaceVariant = Colors.D_BG_TERTIARY,
    onSurfaceVariant = Colors.D_TEXT_SECONDARY,
    outline = Colors.D_SEPARATOR,
    outlineVariant = Colors.D_SEPARATOR,
    error = Colors.D_DANGER,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun NaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = NaiTypography,
        shapes = NaiShapes,
        content = content
    )
}
