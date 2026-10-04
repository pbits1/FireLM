package com.lfmlocal.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LfmDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = TextPrimary,

    secondary = HyperEmerald,
    onSecondary = Color(0xFF012015),
    secondaryContainer = HyperEmeraldContainer,
    onSecondaryContainer = Color(0xFFD1FAE5),

    tertiary = AuraViolet,
    onTertiary = Color.White,
    tertiaryContainer = AuraVioletContainer,
    onTertiaryContainer = Color(0xFFF3E8FF),

    background = ObsidianCanvas,
    onBackground = TextPrimary,

    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighlight,

    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderSubtle,

    error = RadiantRose,
    onError = Color.White
)

val LfmShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LfmTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LfmDarkColorScheme,
        shapes = LfmShapes,
        content = content
    )
}
