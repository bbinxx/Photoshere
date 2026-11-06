package com.bbinxx.photoshere.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PhotoSphereDarkColors = darkColorScheme(
    primary = Aurora,
    onPrimary = Ink,
    primaryContainer = AuroraDim,
    onPrimaryContainer = Ink,
    secondary = Sky,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    background = Ink,
    onBackground = Mist,
    surface = InkElevated,
    onSurface = Mist,
    surfaceVariant = InkSurface,
    onSurfaceVariant = Muted,
    outline = Outline,
    error = Coral,
    onError = Ink,
)

@Composable
fun PhotoshereTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = PhotoSphereDarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
