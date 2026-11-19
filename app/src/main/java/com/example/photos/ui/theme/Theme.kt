package com.example.photos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NothingRed = Color(0xFFD71921)
val CardColor = Color(0xFF151517)
val CardColorHigh = Color(0xFF1C1C1E)
val MutedText = Color(0xFF9A9A9F)

private val Scheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0E0E0E),
    onSurface = Color.White,
    surfaceVariant = CardColorHigh,
    onSurfaceVariant = MutedText,
)

private val AppTypography = Typography().copy(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
