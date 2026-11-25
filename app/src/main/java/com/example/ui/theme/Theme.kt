package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0A241A),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = OnAccentContainerDark,
    secondary = Color(0xFF6AC1CC),
    onSecondary = Color(0xFF0C2A2E),
    secondaryContainer = Color(0xFF1A363A),
    onSecondaryContainer = Color(0xFFC4EEF3),
    tertiary = Color(0xFFE9B262),
    onTertiary = Color(0xFF2E1D05),
    tertiaryContainer = Color(0xFF3A2B16),
    onTertiaryContainer = Color(0xFFFCE0B3),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderDark,
    outlineVariant = Color(0xFF222E29),
    error = Color(0xFFF28B82),
    onError = Color(0xFF42100E),
    errorContainer = Color(0xFF3C1C1B),
    onErrorContainer = Color(0xFFFAD2CE)
)

private val LightColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = AmberTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = CardBorderLight,
    outlineVariant = Color(0xFFEAF0ED),
    error = Color(0xFFC43E3E),
    onError = Color.White,
    errorContainer = Color(0xFFFCECEC),
    onErrorContainer = Color(0xFF7A1C1C)
)

@Composable
fun LapakKitaTheme(
    darkTheme: Boolean = false,
    darkHeader: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkLapakColors else LightLapakColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.surface.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkHeader && !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalLapakColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
