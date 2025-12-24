package com.hyperwhisper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Creates a scaled Typography with all Material 3 text styles
 * @param scale The scale factor to apply to all text sizes (default 1.0)
 * @param fontFamily The font family to use for all text (default FontFamily.Default)
 */
fun createScaledTypography(
    scale: Float = 1.0f,
    fontFamily: FontFamily = FontFamily.Default
): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (57.sp.value * scale).sp,
            lineHeight = (64.sp.value * scale).sp,
            letterSpacing = (-0.25.sp.value * scale).sp
        ),
        displayMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (45.sp.value * scale).sp,
            lineHeight = (52.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (36.sp.value * scale).sp,
            lineHeight = (44.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (32.sp.value * scale).sp,
            lineHeight = (40.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (28.sp.value * scale).sp,
            lineHeight = (36.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (24.sp.value * scale).sp,
            lineHeight = (32.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (22.sp.value * scale).sp,
            lineHeight = (28.sp.value * scale).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (16.sp.value * scale).sp,
            lineHeight = (24.sp.value * scale).sp,
            letterSpacing = (0.15.sp.value * scale).sp
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14.sp.value * scale).sp,
            lineHeight = (20.sp.value * scale).sp,
            letterSpacing = (0.1.sp.value * scale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16.sp.value * scale).sp,
            lineHeight = (24.sp.value * scale).sp,
            letterSpacing = (0.5.sp.value * scale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14.sp.value * scale).sp,
            lineHeight = (20.sp.value * scale).sp,
            letterSpacing = (0.25.sp.value * scale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12.sp.value * scale).sp,
            lineHeight = (16.sp.value * scale).sp,
            letterSpacing = (0.4.sp.value * scale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14.sp.value * scale).sp,
            lineHeight = (20.sp.value * scale).sp,
            letterSpacing = (0.1.sp.value * scale).sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12.sp.value * scale).sp,
            lineHeight = (16.sp.value * scale).sp,
            letterSpacing = (0.5.sp.value * scale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11.sp.value * scale).sp,
            lineHeight = (16.sp.value * scale).sp,
            letterSpacing = (0.5.sp.value * scale).sp
        )
    )
}
