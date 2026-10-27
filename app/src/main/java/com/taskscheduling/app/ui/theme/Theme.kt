package com.taskscheduling.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.taskscheduling.app.domain.model.ThemeMode

// =========================================================================
// CUSTOM APP DESIGN TOKENS
// =========================================================================

data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val cardBorderSubtle: Color,
    val primary: Color,
    val primaryHover: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardBrush: Brush,
    val cardRimBrush: Brush,
    val primaryButtonBrush: Brush,
    val secondaryButtonBrush: Brush,
    val inputBackground: Color,
    val inputBorder: Color,
    val shadowColor: Color
) {
    val surfaceSecondary: Color get() = cardElevated
    val borderSubtle: Color get() = cardBorderSubtle
}

val DarkAppColors = AppColors(
    isDark = true,
    background = BgDeepDark,
    surface = BgSurfaceDark,
    cardBackground = Color(0xCC1E293B),
    cardElevated = BgCardElevatedDark,
    cardBorder = BorderSubtleDark,
    cardBorderSubtle = Color(0x2638BDF8),
    primary = BrandPrimary,
    primaryHover = BrandPrimaryHover,
    secondary = BrandCyan,
    accent = BrandAccent,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    cardBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF1E293B),
            Color(0xFF162031)
        )
    ),
    cardRimBrush = Brush.verticalGradient(
        listOf(
            Color(0x4D38BDF8),
            Color(0x1A94A3B8),
            Color(0x0D1E293B)
        )
    ),
    primaryButtonBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF6366F1),
            Color(0xFF4F46E5)
        )
    ),
    secondaryButtonBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF263348),
            Color(0xFF1E293B)
        )
    ),
    inputBackground = Color(0xFF1E293B),
    inputBorder = Color(0xFF334155),
    shadowColor = Color(0x66000000)
)

val LightAppColors = AppColors(
    isDark = false,
    background = BgDeepLight,
    surface = BgSurfaceLight,
    cardBackground = Color(0xFFFFFFFF),
    cardElevated = Color(0xFFF1F5F9),
    cardBorder = BorderSubtleLight,
    cardBorderSubtle = Color(0xFFE2E8F0),
    primary = BrandPrimary,
    primaryHover = BrandPrimaryHover,
    secondary = BrandDeepCyan,
    accent = BrandAccent,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    cardBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFFFFFFF)
        )
    ),
    cardRimBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFE2E8F0),
            Color(0xFFCBD5E1)
        )
    ),
    primaryButtonBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF6366F1),
            Color(0xFF4F46E5)
        )
    ),
    secondaryButtonBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFF8FAFC),
            Color(0xFFF1F5F9)
        )
    ),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFCBD5E1),
    shadowColor = Color(0x140F172A)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

// =========================================================================
// MATERIAL 3 COLOR SCHEMES
// =========================================================================

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = TextPrimaryDark,
    secondary = BrandCyan,
    onSecondary = BgDeepDark,
    background = BgDeepDark,
    onBackground = TextPrimaryDark,
    surface = BgSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BgCardDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF4338CA),
    secondary = BrandDeepCyan,
    onSecondary = Color.White,
    background = BgDeepLight,
    onBackground = TextPrimaryLight,
    surface = BgSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BgCardElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight
)

@Composable
fun TaskSchedulingTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
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

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

