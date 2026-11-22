package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

@Immutable
data class StudioExtendedColors(
    val tallyRed: Color,
    val vuGreen: Color,
    val vuAmber: Color,
    val vuRed: Color,
    val eyeLineHighlight: Color,
    val eyeLineBorder: Color,
    val subtleDivider: Color,
    val isDark: Boolean,
)

val LocalStudioColors = staticCompositionLocalOf {
    StudioExtendedColors(
        tallyRed = TallyCrimson,
        vuGreen = VuGreen,
        vuAmber = VuAmber,
        vuRed = VuRed,
        eyeLineHighlight = StudioAmber.copy(alpha = 0.16f),
        eyeLineBorder = StudioAmber.copy(alpha = 0.55f),
        subtleDivider = DarkCharcoalOutlineSubtle,
        isDark = true,
    )
}

private val EyelineDarkColorScheme = darkColorScheme(
    primary = StudioAmber,
    onPrimary = StudioAmberOnPrimary,
    primaryContainer = StudioAmberContainerDark,
    onPrimaryContainer = StudioAmberOnContainerDark,
    secondary = StudioAmberBright,
    onSecondary = StudioAmberOnPrimary,
    secondaryContainer = DarkCharcoalContainerHigh,
    onSecondaryContainer = DarkOnSurface,
    tertiary = VuGreen,
    onTertiary = DarkCharcoalBg,
    background = DarkCharcoalBg,
    onBackground = DarkOnSurface,
    surface = DarkCharcoalSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCharcoalContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkCharcoalBg,
    surfaceContainerLow = DarkCharcoalSurface,
    surfaceContainer = DarkCharcoalContainer,
    surfaceContainerHigh = DarkCharcoalContainerHigh,
    surfaceContainerHighest = Color(0xFF333741),
    outline = DarkCharcoalOutline,
    outlineVariant = DarkCharcoalOutlineSubtle,
    error = TallyCrimson,
    onError = Color.White,
    errorContainer = TallyCrimsonDarkContainer,
    onErrorContainer = Color(0xFFFFB4B6),
)

private val EyelineLightColorScheme = lightColorScheme(
    primary = StudioAmberLight,
    onPrimary = StudioAmberOnPrimaryLight,
    primaryContainer = StudioAmberContainerLight,
    onPrimaryContainer = StudioAmberOnContainerLight,
    secondary = StudioAmber,
    onSecondary = StudioAmberOnPrimary,
    secondaryContainer = LightIvoryContainerHigh,
    onSecondaryContainer = LightOnSurface,
    tertiary = Color(0xFF0E8A5F),
    onTertiary = Color.White,
    background = LightIvoryBg,
    onBackground = LightOnSurface,
    surface = LightIvorySurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightIvoryContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightIvorySurface,
    surfaceContainer = LightIvoryContainer,
    surfaceContainerHigh = LightIvoryContainerHigh,
    surfaceContainerHighest = Color(0xFFD9D4C8),
    outline = LightIvoryOutline,
    outlineVariant = LightIvoryOutlineSubtle,
    error = Color(0xFFC93338),
    onError = Color.White,
    errorContainer = TallyCrimsonLightContainer,
    onErrorContainer = Color(0xFF5D0A0E),
)

private val EyelineShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EyelineTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) EyelineDarkColorScheme else EyelineLightColorScheme
    val extendedColors = StudioExtendedColors(
        tallyRed = TallyCrimson,
        vuGreen = VuGreen,
        vuAmber = if (useDark) StudioAmber else StudioAmberLight,
        vuRed = VuRed,
        eyeLineHighlight = StudioAmber.copy(alpha = 0.16f),
        eyeLineBorder = StudioAmber.copy(alpha = 0.65f),
        subtleDivider = if (useDark) DarkCharcoalOutlineSubtle else LightIvoryOutlineSubtle,
        isDark = useDark,
    )

    CompositionLocalProvider(LocalStudioColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EyelineTypography,
            shapes = EyelineShapes,
            content = content,
        )
    }
}
