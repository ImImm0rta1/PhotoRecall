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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.photos.R
import com.example.photos.data.ThemeStyle

/** How the floating bottom navigation looks. */
enum class NavStyle { OUTLINE_LABELS, FLOATING_DOT, CENTER_BUTTON }

/** How album cards look. */
enum class CardStyle { OVERLAY, CLASSIC, BADGE }

/** How the editor's tool bar looks. */
enum class EditorStyle { LABELED, CIRCLES, PILLS }

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
    val bodyFont: FontFamily,
    val titleSize: TextUnit,
    val navStyle: NavStyle,
    val cardStyle: CardStyle,
    val editorStyle: EditorStyle,
)

private val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_regular, FontWeight.Medium),
    Font(R.font.outfit_bold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)
private val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
private val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_regular, FontWeight.Medium),
    Font(R.font.instrument_sans_bold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)
private val Italiana = FontFamily(Font(R.font.italiana_regular, FontWeight.Normal))

private fun vertical(a: Color, b: Color): Brush = Brush.verticalGradient(listOf(a, b))
private fun vertical(a: Color, b: Color, c: Color): Brush = Brush.verticalGradient(listOf(a, b, c))

fun paletteOf(style: ThemeStyle): Palette = when (style) {
    // Navy + aqua, geometric sans (Outfit), outlined labelled nav, image cards with overlaid titles.
    ThemeStyle.LAGOON -> Palette(
        isLight = false, background = Color(0xFF0A0D1C),
        backgroundBrush = vertical(Color(0xFF131A36), Color(0xFF0A0D1C)),
        card = Color(0xFF1A2140), cardHigh = Color(0xFF252E52),
        accent = Color(0xFF8FDCEB), onAccent = Color(0xFF08212A),
        text = Color.White, muted = Color(0xFF8D97B8),
        titleFont = Outfit, bodyFont = Outfit, titleSize = 30.sp,
        navStyle = NavStyle.OUTLINE_LABELS, cardStyle = CardStyle.OVERLAY, editorStyle = EditorStyle.LABELED,
    )
    // Light, warm peach with an editorial serif (Instrument Serif), floating icon nav, white cards.
    ThemeStyle.PEACH -> Palette(
        isLight = true, background = Color(0xFFF7EEEA),
        backgroundBrush = vertical(Color(0xFFF8DCD2), Color(0xFFF9EBE6), Color(0xFFF4EFEC)),
        card = Color(0xE6FFFFFF), cardHigh = Color(0xFFFFFFFF),
        accent = Color(0xFF161412), onAccent = Color.White,
        text = Color(0xFF1E1A19), muted = Color(0xFF8F8480),
        titleFont = InstrumentSerif, bodyFont = InstrumentSans, titleSize = 38.sp,
        navStyle = NavStyle.FLOATING_DOT, cardStyle = CardStyle.CLASSIC, editorStyle = EditorStyle.CIRCLES,
    )
    // Wine red + warm orange, thin display serif (Italiana), centre-button nav, badge cards.
    ThemeStyle.CRIMSON -> Palette(
        isLight = false, background = Color(0xFF12050A),
        backgroundBrush = vertical(Color(0xFF701A2A), Color(0xFF230A10), Color(0xFF0D0407)),
        card = Color(0xFF1E0E12), cardHigh = Color(0xFF2B151A),
        accent = Color(0xFFFF6A3D), onAccent = Color.White,
        text = Color.White, muted = Color(0xFFB69197),
        titleFont = Italiana, bodyFont = Outfit, titleSize = 36.sp,
        navStyle = NavStyle.CENTER_BUTTON, cardStyle = CardStyle.BADGE, editorStyle = EditorStyle.PILLS,
    )
}

val LocalPalette = staticCompositionLocalOf { paletteOf(ThemeStyle.LAGOON) }

// Kept under the old names so every screen follows the active theme automatically.
val NothingRed: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val CardColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val CardColorHigh: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardHigh
val MutedText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted

private fun Typography.withBody(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f), titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f), labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun GalleryTheme(style: ThemeStyle = ThemeStyle.LAGOON, content: @Composable () -> Unit) {
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
    val serifTitle = p.titleFont == InstrumentSerif || p.titleFont == Italiana
    val typography = Typography().withBody(p.bodyFont).copy(
        headlineLarge = TextStyle(
            fontFamily = p.titleFont,
            fontWeight = if (serifTitle) FontWeight.Normal else FontWeight.SemiBold,
            fontSize = p.titleSize,
            lineHeight = p.titleSize * 1.15f,
            letterSpacing = if (serifTitle) 0.sp else (-0.5).sp,
        ),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
