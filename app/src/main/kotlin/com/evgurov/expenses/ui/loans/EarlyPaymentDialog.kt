package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.EarlyMode
import com.evgurov.expenses.domain.usecase.EarlyRepayment
import com.evgurov.expenses.domain.usecase.EarlyResult
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.ui.components.shortTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Ввод досрочного платежа, который пользователь уже внёс в банке, с предпросмотром нового графика. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlyPaymentDialog(
    loan: Loan,
    onSave: (EarlyResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableStateOf(EarlyMode.REDUCE_TERM) }
    var pickDate by remember { mutableStateOf(false) }

    val amount = Money.parse(amountText)?.takeIf { it > 0 }
    val preview = remember(amount, date, mode, loan) {
        amount?.let { runCatching { EarlyRepayment.apply(loan, date, it, mode) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Досрочный платёж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Дата: ${date.shortTitle()}")
                }
                if (!loan.isCard) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip(mode == EarlyMode.REDUCE_TERM, { mode = EarlyMode.REDUCE_TERM }, "Уменьшить срок")
                        PillChip(mode == EarlyMode.REDUCE_PAYMENT, { mode = EarlyMode.REDUCE_PAYMENT }, "Уменьшить платёж")
                    }
                }
                val result = preview?.getOrNull()
                val error = preview?.exceptionOrNull()?.message
                Text(
                    when {
                        error != null -> error
                        result == null -> "Укажите сумму, которую вы внесли в банке."
                        loan.isCard -> "Долг по карте станет ${Money.format(result.balanceAfter)}."
                        else -> buildString {
                            append("Остаток станет ${Money.format(result.balanceAfter)}. ")
                            append("Платёж ${Money.format(result.newPayment)} в месяц")
                            result.newEndDate?.let { append(", последний платёж ${it.shortTitle()}") }
                            append(". Процентов меньше примерно на ${Money.format(result.savedInterest)}. ")
                            append("Это расчёт приложения: точный новый график даст банк.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { preview?.getOrNull()?.let(onSave) },
                enabled = preview?.isSuccess == true,
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
