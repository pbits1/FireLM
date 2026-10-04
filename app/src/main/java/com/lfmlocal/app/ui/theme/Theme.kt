package com.lfmlocal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = DarkTextPrimary,

    secondary = DarkTextSecondary,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkTextPrimary,

    tertiary = DarkTextMuted,
    onTertiary = DarkOnAccent,
    tertiaryContainer = DarkSurface,
    onTertiaryContainer = DarkTextPrimary,

    background = DarkCanvas,
    onBackground = DarkTextPrimary,

    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceHighlight,

    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,

    error = DarkDestructive,
    onError = DarkAccent
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = LightTextPrimary,

    secondary = LightTextSecondary,
    onSecondary = LightOnAccent,
    secondaryContainer = LightSurface,
    onSecondaryContainer = LightTextPrimary,

    tertiary = LightTextMuted,
    onTertiary = LightOnAccent,
    tertiaryContainer = LightSurface,
    onTertiaryContainer = LightTextPrimary,

    background = LightCanvas,
    onBackground = LightTextPrimary,

    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = LightSurfaceHighlight,

    outline = LightBorder,
    outlineVariant = LightBorderSubtle,

    error = LightDestructive,
    onError = LightOnAccent
)

val LfmShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LfmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LfmTypography,
        shapes = LfmShapes,
        content = content
    )
}
