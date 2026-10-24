package com.evgurov.expenses.ui.transactions

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.components.label
import com.evgurov.expenses.ui.components.shortTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    onDone: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая операция") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val kinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = form.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                    ) { Text(kind.label) }
                }
            }

            OutlinedTextField(

                colors = appFieldColors(),
                value = form.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text("Сумма, ₽") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Категория")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { category ->
                    PillChip(selected = form.categoryId == category.id, onClick = { viewModel.setCategory(category.id) }, label = category.name)
                }
            }

            SectionTitle("Счёт")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.forEach { account ->
                    PillChip(selected = state.selectedAccountId == account.id, onClick = { viewModel.setAccount(account.id) }, label = account.name)
                }
            }

            OutlinedButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Text(form.date.shortTitle(), modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedTextField(

                colors = appFieldColors(),
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
            )

            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setDate(it.toLocalDate()) }
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/** DatePicker отдаёт полночь по UTC. */
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
