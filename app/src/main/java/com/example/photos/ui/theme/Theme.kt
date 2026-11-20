package com.example.photos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.photos.data.ThemeStyle

@Immutable
class Palette(
    val isLight: Boolean,
    val background: Color,
    val backgroundBrush: Brush,
    val card: Color,
    val cardHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val muted: Color,
    val titleFont: FontFamily,
)

private fun vertical(a: Color, b: Color): Brush = Brush.verticalGradient(listOf(a, b))
private fun vertical(a: Color, b: Color, c: Color): Brush = Brush.verticalGradient(listOf(a, b, c))

fun paletteOf(style: ThemeStyle): Palette = when (style) {
    // Nothing-phone style: pure black, red accent, serif titles.
    ThemeStyle.NOTHING -> Palette(
        isLight = false, background = Color.Black, backgroundBrush = SolidColor(Color.Black),
        card = Color(0xFF151517), cardHigh = Color(0xFF1F1F22),
        accent = Color(0xFFD71921), onAccent = Color.White,
        text = Color.White, muted = Color(0xFF9A9A9F), titleFont = FontFamily.Serif,
    )
    // Deep blue glass (Reface-style).
    ThemeStyle.AURORA -> Palette(
        isLight = false, background = Color(0xFF0A0E16),
        backgroundBrush = vertical(Color(0xFF1B2638), Color(0xFF0C111B), Color(0xFF080B12)),
        card = Color(0xFF182131), cardHigh = Color(0xFF223049),
        accent = Color(0xFF3B8CFF), onAccent = Color.White,
        text = Color.White, muted = Color(0xFF8D9AB0), titleFont = FontFamily.SansSerif,
    )
    // Navy + aqua (photo-editor style).
    ThemeStyle.LAGOON -> Palette(
        isLight = false, background = Color(0xFF080A14),
        backgroundBrush = vertical(Color(0xFF111833), Color(0xFF080A14)),
        card = Color(0xFF192039), cardHigh = Color(0xFF242E4F),
        accent = Color(0xFF8FDCEB), onAccent = Color(0xFF062029),
        text = Color.White, muted = Color(0xFF8993B3), titleFont = FontFamily.SansSerif,
    )
    // Light, warm peach (albums-app style).
    ThemeStyle.PEACH -> Palette(
        isLight = true, background = Color(0xFFF7EEEA),
        backgroundBrush = vertical(Color(0xFFF9DDD3), Color(0xFFF8EDE8), Color(0xFFF4EFEC)),
        card = Color(0xCCFFFFFF), cardHigh = Color(0xFFFFFFFF),
        accent = Color(0xFF151313), onAccent = Color.White,
        text = Color(0xFF1E1A19), muted = Color(0xFF8F8480), titleFont = FontFamily.SansSerif,
    )
    // Wine red (memories style).
    ThemeStyle.CRIMSON -> Palette(
        isLight = false, background = Color(0xFF12050A),
        backgroundBrush = vertical(Color(0xFF731A2B), Color(0xFF260A11), Color(0xFF0E0407)),
        card = Color(0xFF241015), cardHigh = Color(0xFF331920),
        accent = Color(0xFFFF5B3A), onAccent = Color.White,
        text = Color.White, muted = Color(0xFFB59197), titleFont = FontFamily.SansSerif,
    )
    // Ultraviolet (product-photo style).
    ThemeStyle.VIOLET -> Palette(
        isLight = false, background = Color(0xFF110A1D),
        backgroundBrush = vertical(Color(0xFF3B1E5E), Color(0xFF1A0F2B), Color(0xFF0F0919)),
        card = Color(0xFF241638), cardHigh = Color(0xFF33214F),
        accent = Color(0xFFB847E4), onAccent = Color.White,
        text = Color.White, muted = Color(0xFFA596C2), titleFont = FontFamily.SansSerif,
    )
}

val LocalPalette = staticCompositionLocalOf { paletteOf(ThemeStyle.NOTHING) }

// Kept under the old names so every screen follows the active theme automatically.
val NothingRed: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val CardColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val CardColorHigh: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardHigh
val MutedText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted

@Composable
fun GalleryTheme(style: ThemeStyle = ThemeStyle.NOTHING, content: @Composable () -> Unit) {
    val p = remember(style) { paletteOf(style) }
    val scheme = if (p.isLight) {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.cardHigh, onSurfaceVariant = p.muted,
            surfaceContainer = p.cardHigh, surfaceContainerHigh = p.cardHigh, surfaceContainerLow = p.cardHigh,
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.cardHigh, onSurfaceVariant = p.muted,
            surfaceContainer = p.cardHigh, surfaceContainerHigh = p.cardHigh, surfaceContainerLow = p.cardHigh,
        )
    }
    val serif = p.titleFont == FontFamily.Serif
    val typography = Typography().copy(
        headlineLarge = TextStyle(
            fontFamily = p.titleFont,
            fontWeight = if (serif) FontWeight.Normal else FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
        ),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
