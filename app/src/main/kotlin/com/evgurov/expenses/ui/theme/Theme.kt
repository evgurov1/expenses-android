package com.evgurov.expenses.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Монохромная палитра: чёрный для действий, белые карточки на светло-сером фоне.
// Цвет — только для смысла: доход, зоны ПДН, ошибки.
val Ink = Color(0xFF101010)
val Graphite = Color(0xFF242424)
val Slate = Color(0xFF6B7280)
val Stone = Color(0xFF898989)
val Silver = Color(0xFFE5E7EB)
val Paper = Color(0xFFF4F4F4)
val White = Color(0xFFFFFFFF)
val ActionBlue = Color(0xFF0099FF)
val InfoBackground = Color(0xFFEFF6FE)

val IncomeColor = Color(0xFF15803D)

private val Colors = lightColorScheme(
    primary = Ink,
    onPrimary = White,
    primaryContainer = Ink,
    onPrimaryContainer = White,
    secondary = Graphite,
    onSecondary = White,
    secondaryContainer = Silver,
    onSecondaryContainer = Graphite,
    tertiary = ActionBlue,
    onTertiary = White,
    tertiaryContainer = InfoBackground,
    onTertiaryContainer = Graphite,
    background = Paper,
    onBackground = Graphite,
    surface = Paper,
    onSurface = Graphite,
    surfaceVariant = Silver,
    onSurfaceVariant = Slate,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = White,
    surfaceBright = White,
    surfaceDim = Paper,
    outline = Color(0xFFD1D5DB),
    outlineVariant = Silver,
    error = Color(0xFFDC2626),
    onError = White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Ink,
    inverseOnSurface = White,
    inversePrimary = Silver,
    scrim = Color.Black,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Тема всегда светлая: так задумано в дизайне. */
@Composable
fun ExpensesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, shapes = AppShapes, content = content)
}
