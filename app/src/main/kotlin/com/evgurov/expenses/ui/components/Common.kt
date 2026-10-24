package com.evgurov.expenses.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.theme.IncomeColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

val RussianLocale: Locale = Locale.forLanguageTag("ru-RU")

val AccountType.label: String
    get() = when (this) {
        AccountType.CARD -> "Карта"
        AccountType.CASH -> "Наличные"
        AccountType.DEPOSIT -> "Вклад"
        AccountType.OTHER -> "Другое"
    }

val TransactionKind.label: String
    get() = when (this) {
        TransactionKind.INCOME -> "Доход"
        TransactionKind.EXPENSE -> "Расход"
    }

fun YearMonth.title(): String {
    val name = month.getDisplayName(JavaTextStyle.FULL_STANDALONE, RussianLocale)
        .replaceFirstChar { it.titlecase(RussianLocale) }
    return if (year == YearMonth.now().year) name else "$name $year"
}

private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", RussianLocale)
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RussianLocale)

fun LocalDate.dayTitle(): String = when (this) {
    LocalDate.now() -> "Сегодня"
    LocalDate.now().minusDays(1) -> "Вчера"
    else -> format(dayFormatter)
}

fun LocalDate.shortTitle(): String = format(shortDateFormatter)

/** Сумма с цветом: доходы зелёные со знаком «+», расходы обычным цветом со знаком «−». */
@Composable
fun SignedAmount(
    amount: Kopecks,
    kind: TransactionKind,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val text = Money.format(amount)
    Text(
        text = if (kind == TransactionKind.INCOME) "+$text" else "−$text",
        color = if (kind == TransactionKind.INCOME) IncomeColor else MaterialTheme.colorScheme.onSurface,
        style = style,
        modifier = modifier,
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private val CardShape = RoundedCornerShape(12.dp)

/** Белая карточка без рамки с мягкой тенью — основной блок интерфейса. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadowed = modifier.shadow(
        elevation = 6.dp,
        shape = CardShape,
        ambientColor = Color(0x0D242424),
        spotColor = Color(0x1A242424),
    )
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val body: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = shadowed, shape = CardShape, colors = colors, content = body)
    } else {
        Card(modifier = shadowed, shape = CardShape, colors = colors, content = body)
    }
}

/** Небольшой заголовок раздела над карточками. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

/** Фильтр-таблетка: выбранная — чёрная, остальные — белые с тонкой рамкой. */
@Composable
fun PillChip(selected: Boolean, onClick: () -> Unit, label: String) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.padding(vertical = 6.dp)) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerLowest,
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.outlineVariant,
            selectedBorderColor = colors.primary,
        ),
    )
}

/** Поля ввода: белые на сером фоне, рамка чёрная при фокусе. */
@Composable
fun appFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceContainerLowest,
        unfocusedContainerColor = colors.surfaceContainerLowest,
        errorContainerColor = colors.surfaceContainerLowest,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        focusedLabelColor = colors.onSurface,
        cursorColor = colors.primary,
    )
}
