package com.evgurov.expenses.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.evgurov.expenses.R

/** Заголовки: геометрический Montserrat 600 (замена Cal Sans с кириллицей). */
val Headline = FontFamily(Font(R.font.montserrat_semibold, FontWeight.SemiBold))

/** Текст и интерфейс: Inter. */
val Body = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun heading(size: Int, line: Double) = TextStyle(
    fontFamily = Headline,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = 0.01.em,
)

private fun body(size: Int, line: Double, weight: FontWeight = FontWeight.Normal, spacing: Double = -0.012) = TextStyle(
    fontFamily = Body,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = spacing.em,
)

val AppTypography = Typography(
    displayLarge = heading(56, 1.1),
    displayMedium = heading(44, 1.1),
    displaySmall = heading(36, 1.1),
    headlineLarge = heading(30, 1.2),
    headlineMedium = heading(26, 1.2),
    headlineSmall = heading(22, 1.3),
    titleLarge = heading(20, 1.3),
    titleMedium = body(16, 1.4, FontWeight.SemiBold),
    titleSmall = body(14, 1.4, FontWeight.SemiBold),
    bodyLarge = body(16, 1.5),
    bodyMedium = body(14, 1.5),
    bodySmall = body(12, 1.4),
    labelLarge = body(14, 1.4, FontWeight.Medium),
    labelMedium = body(12, 1.4, FontWeight.Medium),
    labelSmall = body(11, 1.4, FontWeight.Medium, 0.0),
)
