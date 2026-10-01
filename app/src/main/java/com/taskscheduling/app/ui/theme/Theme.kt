package com.taskscheduling.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.taskscheduling.app.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BgDeepDark,
    primaryContainer = DeepCyan,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentIndigo,
    onSecondary = TextPrimaryDark,
    background = BgDeepDark,
    onBackground = TextPrimaryDark,
    surface = BgSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BgCardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = BgDeepLight,
    primaryContainer = NeonCyan.copy(alpha = 0.2f),
    onPrimaryContainer = DeepCyan,
    secondary = AccentIndigo,
    onSecondary = BgDeepLight,
    background = BgDeepLight,
    onBackground = TextPrimaryLight,
    surface = BgSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BgCardLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight
)

@Composable
fun TaskSchedulingTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
