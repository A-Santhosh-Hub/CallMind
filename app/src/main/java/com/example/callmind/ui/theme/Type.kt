package com.example.callmind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp
)

/** Sizes follow the reference: row title 17, subtitle 14-15, category title 14, summary 13. */
val Typography = Typography(
    displayLarge = style(FontWeight.Bold, 32, 40),
    headlineLarge = style(FontWeight.SemiBold, 28, 36),
    headlineMedium = style(FontWeight.SemiBold, 24, 32),
    titleLarge = style(FontWeight.Medium, 22, 28),
    titleMedium = style(FontWeight.Medium, 17, 24),
    titleSmall = style(FontWeight.Medium, 15, 20),
    bodyLarge = style(FontWeight.Normal, 17, 24),
    bodyMedium = style(FontWeight.Normal, 15, 20),
    bodySmall = style(FontWeight.Normal, 13, 18),
    labelLarge = style(FontWeight.Medium, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 16),
    labelSmall = style(FontWeight.Medium, 11, 16)
)
