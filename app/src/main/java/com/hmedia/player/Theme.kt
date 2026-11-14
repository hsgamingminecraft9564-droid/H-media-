package com.hmedia.player

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// A theme with a photo also sets that picture as the background when it is chosen
enum class HTheme(
    val label: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val text: Color,
    val photo: String? = null
) {
    BLOOD_IRON("Blood & Iron", Color(0xFF0B0B0C), Color(0xFF1A1A1D), Color(0xFFB0102A), Color(0xFFD9D9DE)),
    ROYAL_GOLD("Royal Gold", Color(0xFF0E0B07), Color(0xFF1E1810), Color(0xFFC9A24A), Color(0xFFF2E6C9)),
    NEON_ROYAL("Neon Royal", Color(0xFF0C0716), Color(0xFF1B1030), Color(0xFFA855F7), Color(0xFFEDE4FF)),
    TOXIC_GREEN("Toxic Green", Color(0xFF070A03), Color(0xFF131A07), Color(0xFFB8F230), Color(0xFFE7F7C4)),
    TORII_DUSK("Torii Dusk", Color(0xFF0B0E10), Color(0xFF1A2023), Color(0xFFE8743B), Color(0xFFF3E6D6), "torii"),
    ALPENGLOW("Alpenglow", Color(0xFF0D0A0C), Color(0xFF1D1618), Color(0xFFE9906A), Color(0xFFF6E3DA), "alpenglow"),
    GLACIER("Glacier", Color(0xFF080F11), Color(0xFF142024), Color(0xFF5FB4C2), Color(0xFFDDEEF0), "glacier"),
    MISTY_FOREST("Misty Forest", Color(0xFF08100C), Color(0xFF121E19), Color(0xFF4CC9A0), Color(0xFFE0F0E8), "misty")
}

// Medieval is the default. Put the real medieval .ttf in res/font to match the splash screen.
enum class HFont(val label: String, val family: FontFamily) {
    MEDIEVAL("Medieval (default)", FontFamily.Serif),
    MODERN("Modern", FontFamily.SansSerif),
    TYPEWRITER("Typewriter", FontFamily.Monospace),
    SCRIPT("Script", FontFamily.Cursive)
}

@Composable
fun HMediaTheme(theme: HTheme, font: HFont, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = theme.accent,
            onPrimary = theme.background,
            background = theme.background,
            onBackground = theme.text,
            surface = theme.surface,
            onSurface = theme.text,
            surfaceVariant = theme.surface,
            onSurfaceVariant = theme.text.copy(alpha = 0.75f)
        ),
        typography = Typography().withFamily(font.family),
        content = content
    )
}

// Material 3 has no defaultFontFamily parameter, so the chosen family is set on every text style
private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.use() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.use(), displayMedium = displayMedium.use(), displaySmall = displaySmall.use(),
        headlineLarge = headlineLarge.use(), headlineMedium = headlineMedium.use(), headlineSmall = headlineSmall.use(),
        titleLarge = titleLarge.use(), titleMedium = titleMedium.use(), titleSmall = titleSmall.use(),
        bodyLarge = bodyLarge.use(), bodyMedium = bodyMedium.use(), bodySmall = bodySmall.use(),
        labelLarge = labelLarge.use(), labelMedium = labelMedium.use(), labelSmall = labelSmall.use()
    )
}
