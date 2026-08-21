package com.neojou.tsumego

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font

private val Paper = Color(0xFFF7F1E6)
private val Ink = Color(0xFF2A2118)
private val Hinoki = Color(0xFF4C3A2A)
private val Linen = Color(0xFFE8DCC8)

private val AppColorScheme = lightColorScheme(
    primary = Hinoki,
    onPrimary = Paper,
    primaryContainer = Color(0xFFE4D3B8),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF7A6248),
    onSecondary = Paper,
    background = Color(0xFFF4EDE0),
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainer = Linen,
    surfaceContainerHigh = Color(0xFFDFD2BB),
    outline = Color(0xFF8A7A64),
    error = Color(0xFFB3261E),
)

/**
 * Bundled CJK-capable family — Noto Sans TC Regular (SIL OFL 1.1).
 *
 * Compose Wasm/Desktop draw with Skia, which does **not** pick up browser or
 * CSS fonts. Without a bundled CJK face, Chinese glyphs render as blank.
 */
@Composable
fun appFontFamily(): FontFamily = FontFamily(Font(Res.font.notosanstc_regular))

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)

/**
 * App-wide Material 3 theme with a CJK-capable default typeface.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontFamily = appFontFamily()
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography().withFontFamily(fontFamily),
        content = content,
    )
}
