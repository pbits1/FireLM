package com.lfmlocal.app.ui.theme

import androidx.compose.ui.graphics.Color

// =======================================================================
// Monochromatic Minimalist Palette (Apple / Google / ChatGPT Tier)
// Exactly 3-4 centered tones: Deep Canvas, Soft Graphite, Crisp White, Muted Gray
// =======================================================================

// --- DARK PALETTE (Deep Velvet OLED Dark) ---
val DarkCanvas = Color(0xFF09090B)
val DarkSurface = Color(0xFF141417)
val DarkSurfaceElevated = Color(0xFF1F2024)
val DarkSurfaceHighlight = Color(0xFF2B2C33)

val DarkBorder = Color(0xFF26272C)
val DarkBorderSubtle = Color(0xFF1C1D22)

val DarkTextPrimary = Color(0xFFF4F4F5)
val DarkTextSecondary = Color(0xFFA1A1AA)
val DarkTextMuted = Color(0xFF71717A)

val DarkAccent = Color(0xFFFFFFFF)
val DarkOnAccent = Color(0xFF000000)
val DarkDestructive = Color(0xFFF87171)

// --- LIGHT PALETTE (Crisp Apple Clean White / Silver) ---
val LightCanvas = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF8F8FA)
val LightSurfaceElevated = Color(0xFFF0F0F3)
val LightSurfaceHighlight = Color(0xFFE2E2E7)

val LightBorder = Color(0xFFE4E4E7)
val LightBorderSubtle = Color(0xFFEEEEF0)

val LightTextPrimary = Color(0xFF09090B)
val LightTextSecondary = Color(0xFF52525B)
val LightTextMuted = Color(0xFF8E8E93)

val LightAccent = Color(0xFF000000)
val LightOnAccent = Color(0xFFFFFFFF)
val LightDestructive = Color(0xFFDC2626)

// 1. Canvas & Structural Surfaces (Legacy Aliases)
val MonochromeCanvas = DarkCanvas
val MonochromeSurface = DarkSurface
val MonochromeSurfaceElevated = DarkSurfaceElevated
val MonochromeSurfaceHighlight = DarkSurfaceHighlight

// 2. Hairline Separators & Borders
val MonochromeBorder = DarkBorder
val MonochromeBorderSubtle = DarkBorderSubtle

// 3. High-Legibility Monochromatic Typography
val MonochromeTextPrimary = DarkTextPrimary
val MonochromeTextSecondary = DarkTextSecondary
val MonochromeTextMuted = DarkTextMuted

// 4. Clean High-Contrast Accent (Apple Pure White & Inverted Dark)
val MonochromeWhite = Color(0xFFFFFFFF)
val MonochromeBlack = Color(0xFF000000)
val MonochromeDestructive = DarkDestructive

// =======================================================================
// Semantic References
// =======================================================================
val UserBubbleBg = DarkSurfaceElevated
val UserBubbleBorder = DarkBorderSubtle
val AssistantBubbleBg = Color.Transparent
val CodeBlockBg = Color(0xFF0F0F12)
val CodeBlockBorder = Color(0xFF202126)

// =======================================================================
// Backward-Compatibility Aliases
// =======================================================================
val CarbonCanvas = MonochromeCanvas
val ConsoleSlate = MonochromeSurface
val InsetField = MonochromeSurfaceElevated
val ConsoleHighlight = MonochromeSurfaceHighlight
val ConsoleBorder = MonochromeBorder
val ConsoleBorderSubtle = MonochromeBorderSubtle
val ConsoleBorderStrong = MonochromeBorder
val TextPrimary = MonochromeTextPrimary
val TextSecondary = MonochromeTextSecondary
val TextMuted = MonochromeTextMuted
val TextDisabled = MonochromeTextMuted

// Minimalist accent redirects
val SolarAmber = MonochromeTextPrimary
val SolarAmberDark = MonochromeTextSecondary
val SolarAmberContainer = MonochromeSurfaceElevated
val OnSolarAmber = MonochromeBlack
val PhosphorCyan = MonochromeTextPrimary
val PhosphorCyanDim = MonochromeTextSecondary
val PhosphorCyanContainer = MonochromeSurfaceElevated
val OnPhosphorCyan = MonochromeBlack
val MatrixEmerald = MonochromeTextPrimary
val MatrixEmeraldDark = MonochromeTextSecondary
val MatrixEmeraldContainer = MonochromeSurfaceElevated
val OnMatrixEmerald = MonochromeBlack
val SignalRose = MonochromeDestructive
val SignalRoseDark = MonochromeDestructive
val SignalRoseContainer = MonochromeSurfaceElevated
val OnSignalRose = MonochromeWhite
val WorkstationViolet = MonochromeTextSecondary
val WorkstationVioletContainer = MonochromeSurfaceElevated

val ObsidianCanvas = MonochromeCanvas
val ObsidianSurface = MonochromeSurface
val ObsidianSurfaceElevated = MonochromeSurfaceElevated
val ObsidianSurfaceHighlight = MonochromeSurfaceHighlight
val ObsidianBorder = MonochromeBorder
val ObsidianBorderSubtle = MonochromeBorderSubtle
val ElectricCyan = PhosphorCyan
val ElectricCyanDim = PhosphorCyanDim
val ElectricCyanContainer = PhosphorCyanContainer
val OnElectricCyan = OnPhosphorCyan
val HyperEmerald = MatrixEmerald
val HyperEmeraldDim = MatrixEmeraldDark
val HyperEmeraldContainer = MatrixEmeraldContainer
val AuraViolet = WorkstationViolet
val AuraVioletContainer = WorkstationVioletContainer
val SunsetAmber = SolarAmber
val RadiantRose = SignalRose
val UserBubbleTop = UserBubbleBg
val UserBubbleBottom = UserBubbleBg
val AssistantCardBackground = MonochromeSurface
