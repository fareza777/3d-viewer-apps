package com.studio3dpro.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.R
import com.studio3dpro.app.ThemeMode

/**
 * "Obsidian Atelier" design language: warm paper in light mode, graphite in
 * dark mode, one electric-lilac accent, mint for rigging and amber for
 * selection. The 3D canvas keeps a fixed dark glass chrome in both themes so
 * the model stays the hero.
 */
object StudioColors {
    var isLight = false
        private set

    var Ink = Color(0xFF07080B)
        private set
    var Base = Color(0xFF0B0C10)
        private set
    var Surface = Color(0xFF111318)
        private set
    var SurfaceRaised = Color(0xFF161920)
        private set
    var SurfaceHigh = Color(0xFF1D2129)
        private set
    var Border = Color(0xFF282D37)
        private set
    var BorderSoft = Color(0xFF1E222A)
        private set
    var TextPrimary = Color(0xFFF3F1EC)
        private set
    var TextSecondary = Color(0xFFA6A7B0)
        private set
    var TextMuted = Color(0xFF6E717C)
        private set
    var Accent = Color(0xFFB9A7FF)
        private set
    var AccentDeep = Color(0xFF7C63E8)
        private set
    var Mint = Color(0xFF8BE9D0)
        private set
    var Amber = Color(0xFFFFB574)
        private set
    var Danger = Color(0xFFFF7D7D)
        private set
    var Blue = Color(0xFF6EA8FF)
        private set
    var Green = Color(0xFF74D99F)
        private set
    var AxisX = Color(0xFFFF6B63)
        private set
    var AxisY = Color(0xFF8CD97A)
        private set
    var AxisZ = Color(0xFF5A9BFF)
        private set
    var Scrim = Color(0xCC06070A)
        private set

    // Editor surfaces that have their own density rules.
    var EditorBg = Color(0xFF0D0E13)
        private set
    var EditorRuler = Color(0xFF101218)
        private set
    var EditorRow = Color(0xFF0F1117)
        private set
    var EditorLine = Color(0xFF111318)
        private set

    // Glass chrome for overlays that sit on the 3D canvas; follows the theme so
    // the overlay chips stay legible whether the viewport is ink or warm paper.
    var CanvasGlass = Color(0xB30B0C10)
        private set
    var CanvasGlassSoft = Color(0x8C0B0C10)
        private set
    var CanvasBorder = Color(0x33FFFFFF)
        private set
    var OnCanvasPrimary = Color(0xFFF3F1EC)
        private set
    var OnCanvasSecondary = Color(0xFFB9BAC3)
        private set
    var OnCanvasMuted = Color(0xFF8B8D97)
        private set
    var CanvasInk = Color(0xFF07080B)
        private set
    var CanvasAccent = Color(0xFFB9A7FF)
        private set
    var CanvasMint = Color(0xFF8BE9D0)
        private set
    var CanvasDanger = Color(0xFFFF7D7D)
        private set
    var CanvasAxisX = Color(0xFFFF6B63)
        private set
    var CanvasAxisY = Color(0xFF8CD97A)
        private set
    var CanvasAxisZ = Color(0xFF5A9BFF)
        private set

    fun apply(light: Boolean) {
        isLight = light
        // Canvas tokens follow the chrome so the 3D viewport matches the theme.
        CanvasGlass = if (light) Color(0xB3FBF8F2) else Color(0xB30B0C10)
        CanvasGlassSoft = if (light) Color(0x8CFBF8F2) else Color(0x8C0B0C10)
        CanvasBorder = if (light) Color(0x33000000) else Color(0x33FFFFFF)
        OnCanvasPrimary = if (light) Color(0xFF17171B) else Color(0xFFF3F1EC)
        OnCanvasSecondary = if (light) Color(0xFF4A4C55) else Color(0xFFB9BAC3)
        OnCanvasMuted = if (light) Color(0xFF6E7079) else Color(0xFF8B8D97)
        CanvasInk = if (light) Color(0xFFFBF8F2) else Color(0xFF07080B)
        CanvasAccent = if (light) Color(0xFF6246D8) else Color(0xFFB9A7FF)
        CanvasMint = if (light) Color(0xFF0C7A64) else Color(0xFF8BE9D0)
        CanvasDanger = if (light) Color(0xFFBE3A3A) else Color(0xFFFF7D7D)
        CanvasAxisX = if (light) Color(0xFFCF3F36) else Color(0xFFFF6B63)
        CanvasAxisY = if (light) Color(0xFF3C8737) else Color(0xFF8CD97A)
        CanvasAxisZ = if (light) Color(0xFF2B5FBF) else Color(0xFF5A9BFF)
        if (light) {
            Ink = Color(0xFFF5F1E8)
            Base = Color(0xFFFBF8F2)
            Surface = Color(0xFFFFFFFF)
            SurfaceRaised = Color(0xFFF7F3EB)
            SurfaceHigh = Color(0xFFEDE7DB)
            Border = Color(0xFFD7D0C2)
            BorderSoft = Color(0xFFE6DFD2)
            TextPrimary = Color(0xFF17171B)
            TextSecondary = Color(0xFF585A63)
            TextMuted = Color(0xFF84858E)
            Accent = Color(0xFF6246D8)
            AccentDeep = Color(0xFF4A32B4)
            Mint = Color(0xFF0C7A64)
            Amber = Color(0xFFA45A1A)
            Danger = Color(0xFFBE3A3A)
            Blue = Color(0xFF2D63C6)
            Green = Color(0xFF22784C)
            AxisX = Color(0xFFCF3F36)
            AxisY = Color(0xFF3C8737)
            AxisZ = Color(0xFF2B5FBF)
            Scrim = Color(0xCCF5F1E8)
            EditorBg = Color(0xFFF8F5EE)
            EditorRuler = Color(0xFFF0EBE1)
            EditorRow = Color(0xFFFCFAF5)
            EditorLine = Color(0xFFE2DBCE)
        } else {
            Ink = Color(0xFF07080B)
            Base = Color(0xFF0B0C10)
            Surface = Color(0xFF111318)
            SurfaceRaised = Color(0xFF161920)
            SurfaceHigh = Color(0xFF1D2129)
            Border = Color(0xFF282D37)
            BorderSoft = Color(0xFF1E222A)
            TextPrimary = Color(0xFFF3F1EC)
            TextSecondary = Color(0xFFA6A7B0)
            TextMuted = Color(0xFF6E717C)
            Accent = Color(0xFFB9A7FF)
            AccentDeep = Color(0xFF7C63E8)
            Mint = Color(0xFF8BE9D0)
            Amber = Color(0xFFFFB574)
            Danger = Color(0xFFFF7D7D)
            Blue = Color(0xFF6EA8FF)
            Green = Color(0xFF74D99F)
            AxisX = Color(0xFFFF6B63)
            AxisY = Color(0xFF8CD97A)
            AxisZ = Color(0xFF5A9BFF)
            Scrim = Color(0xCC06070A)
            EditorBg = Color(0xFF0D0E13)
            EditorRuler = Color(0xFF101218)
            EditorRow = Color(0xFF0F1117)
            EditorLine = Color(0xFF111318)
        }
    }
}

val DisplayFamily = FontFamily(Font(R.font.space_grotesk))
val BodyFamily = FontFamily(Font(R.font.inter))

private val StudioTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 44.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 19.sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.7.sp),
)

@Composable
private fun studioScheme() = if (StudioColors.isLight) {
    lightColorScheme(
        primary = StudioColors.Accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE7FF),
        onPrimaryContainer = Color(0xFF2A1D63),
        secondary = StudioColors.Mint,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDF4EC),
        onSecondaryContainer = Color(0xFF083B31),
        tertiary = StudioColors.Amber,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFBEBDD),
        onTertiaryContainer = Color(0xFF4A2508),
        background = StudioColors.Ink,
        onBackground = StudioColors.TextPrimary,
        surface = StudioColors.Surface,
        onSurface = StudioColors.TextPrimary,
        surfaceVariant = StudioColors.SurfaceRaised,
        onSurfaceVariant = StudioColors.TextSecondary,
        outline = StudioColors.Border,
        outlineVariant = StudioColors.BorderSoft,
        error = StudioColors.Danger,
        onError = Color.White,
        scrim = StudioColors.Scrim
    )
} else {
    darkColorScheme(
        primary = StudioColors.Accent,
        onPrimary = Color(0xFF161020),
        primaryContainer = Color(0xFF2A2440),
        onPrimaryContainer = StudioColors.Accent,
        secondary = StudioColors.Mint,
        onSecondary = Color(0xFF061A16),
        secondaryContainer = Color(0xFF11302A),
        onSecondaryContainer = StudioColors.Mint,
        tertiary = StudioColors.Amber,
        onTertiary = Color(0xFF241203),
        background = StudioColors.Ink,
        onBackground = StudioColors.TextPrimary,
        surface = StudioColors.Surface,
        onSurface = StudioColors.TextPrimary,
        surfaceVariant = StudioColors.SurfaceRaised,
        onSurfaceVariant = StudioColors.TextSecondary,
        outline = StudioColors.Border,
        outlineVariant = StudioColors.BorderSoft,
        error = StudioColors.Danger,
        onError = Color(0xFF1D0505),
        scrim = StudioColors.Scrim
    )
}

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StudioTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    StudioColors.apply(light = !dark)
    // Recreate the whole tree on theme changes: the palette is a fast global
    // token table read by hundreds of composables, and this keeps their call
    // sites free of a palette parameter.
    key(dark) {
        MaterialTheme(
            colorScheme = studioScheme(),
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
