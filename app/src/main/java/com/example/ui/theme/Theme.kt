package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromString(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class AccentPalette(val label: String, val previewColor: Color) {
    ROYAL_BLUE("Academic Blue", StudyBluePrimary),
    INDIGO("Deep Indigo", IndigoPrimary),
    TEAL("Scholar Teal", TealAccentPrimary),
    PLUM("Focus Plum", PlumPrimary);

    companion object {
        fun fromString(raw: String?): AccentPalette =
            entries.firstOrNull { it.name == raw } ?: ROYAL_BLUE
    }
}

private fun buildLightColorScheme(palette: AccentPalette): ColorScheme {
    val (primary, primaryContainer) = when (palette) {
        AccentPalette.ROYAL_BLUE -> StudyBluePrimary to StudyBlueContainer
        AccentPalette.INDIGO -> IndigoPrimary to IndigoContainer
        AccentPalette.TEAL -> TealAccentPrimary to TealAccentContainer
        AccentPalette.PLUM -> PlumPrimary to PlumContainer
    }
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = StudyBlueOnContainer,
        secondary = StudyPurpleSecondary,
        onSecondary = Color.White,
        secondaryContainer = StudyPurpleContainer,
        onSecondaryContainer = StudyPurpleOnContainer,
        tertiary = StudyTealTertiary,
        onTertiary = Color.White,
        tertiaryContainer = StudyTealContainer,
        onTertiaryContainer = StudyTealOnContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHigh,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = StatusDanger,
        onError = Color.White
    )
}

private fun buildDarkColorScheme(palette: AccentPalette): ColorScheme {
    val primary = when (palette) {
        AccentPalette.ROYAL_BLUE -> StudyBlueDarkPrimary
        AccentPalette.INDIGO -> IndigoDarkPrimary
        AccentPalette.TEAL -> TealAccentDarkPrimary
        AccentPalette.PLUM -> PlumDarkPrimary
    }
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0A1628),
        primaryContainer = Color(0xFF1E3A8A),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = StudyPurpleDarkSecondary,
        onSecondary = Color(0xFF1E1038),
        secondaryContainer = Color(0xFF3B1F6E),
        onSecondaryContainer = Color(0xFFEDE9FE),
        tertiary = StudyTealDarkTertiary,
        onTertiary = Color(0xFF042F2E),
        tertiaryContainer = Color(0xFF115E59),
        onTertiaryContainer = Color(0xFFCCFBF1),
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = DarkBackground,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHigh,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A)
    )
}

val StudyFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StudyFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.ROYAL_BLUE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (isDark) buildDarkColorScheme(accentPalette) else buildLightColorScheme(accentPalette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudyFlowTypography,
        shapes = StudyFlowShapes,
        content = content
    )
}
