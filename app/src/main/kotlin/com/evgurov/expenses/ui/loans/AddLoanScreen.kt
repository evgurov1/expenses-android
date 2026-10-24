package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.ui.components.shortTitle
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLoanScreen(
    onDone: () -> Unit,
    viewModel: AddLoanViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый кредит") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Тип", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoanType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { viewModel.edit { it.copy(type = type) } },
                        label = { Text(type.label) },
                    )
                }
            }

            TextInput("Банк", form.bank) { v -> viewModel.edit { it.copy(bank = v) } }
            TextInput("Название (необязательно)", form.name) { v -> viewModel.edit { it.copy(name = v) } }

            if (form.isCard) {
                MoneyInput("Кредитный лимит, ₽", form.limitText) { v -> viewModel.edit { it.copy(limitText = v) } }
                MoneyInput("Текущий долг по карте, ₽", form.balanceText) { v -> viewModel.edit { it.copy(balanceText = v) } }
                NumberInput("Ставка, % годовых", form.rateText) { v -> viewModel.edit { it.copy(rateText = v) } }
                form.calculatedPayment?.let {
                    Hint("Для ПДН банки считают платёж по карте как 5% лимита: ${Money.format(it)} в месяц, даже если долга нет.")
                }
            } else {
                MoneyInput("Сумма кредита, ₽", form.principalText) { v -> viewModel.edit { it.copy(principalText = v) } }
                NumberInput("Ставка, % годовых", form.rateText) { v -> viewModel.edit { it.copy(rateText = v) } }
                NumberInput("Срок, месяцев", form.termText, KeyboardType.Number) { v -> viewModel.edit { it.copy(termText = v) } }

                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Text("Выдан ${form.issueDate.shortTitle()}", modifier = Modifier.padding(start = 8.dp))
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentType.entries.forEach { type ->
                        FilterChip(
                            selected = form.paymentType == type,
                            onClick = { viewModel.edit { it.copy(paymentType = type) } },
                            label = { Text(type.label) },
                        )
                    }
                }

                MoneyInput("Ежемесячный платёж из договора, ₽", form.paymentText) { v ->
                    viewModel.edit { it.copy(paymentText = v) }
                }
                form.calculatedPayment?.let {
                    Hint("По сумме, ставке и сроку платёж ≈ ${Money.format(it)}. Если в договоре другая сумма, впишите её.")
                }
                MoneyInput("Остаток долга сейчас, ₽ (если знаете)", form.balanceText) { v ->
                    viewModel.edit { it.copy(balanceText = v) }
                }
            }

            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.issueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.edit { it.copy(issueDate = date) }
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TextInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MoneyInput(label: String, value: String, onChange: (String) -> Unit) =
    NumberInput(label, value, KeyboardType.Decimal, onChange)

@Composable
private fun NumberInput(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
