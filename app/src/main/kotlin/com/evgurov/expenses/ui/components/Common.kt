package com.evgurov.expenses.ui.components

import androidx.compose.material3.AlertDialog
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
