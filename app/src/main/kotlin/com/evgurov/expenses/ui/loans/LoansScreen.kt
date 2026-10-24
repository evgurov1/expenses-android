package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.IncomeBasis
import com.evgurov.expenses.ui.components.shortTitle
import com.evgurov.expenses.ui.components.title
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onAddLoan: () -> Unit,
    onImportLoan: () -> Unit,
    onOpenLoan: (Long) -> Unit,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddIncome by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Кредиты") }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onImportLoan,
                    icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                    text = { Text("Из PDF или фото") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                ExtendedFloatingActionButton(
                    onClick = onAddLoan,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Вручную") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 168.dp),
        ) {
            item { BurdenCard(state) }

            item { SectionHeader("Доход в месяц") }
            if (state.incomeSources.isEmpty()) {
                item {
                    Text(
                        text = when (state.incomeBasis) {
                            IncomeBasis.TRANSACTIONS ->
                                "Считаем по доходам из операций за 3 месяца: ${Money.format(state.burden.income)}. " +
                                    "Укажите доход явно, чтобы ПДН была точнее."
                            else -> "Укажите доход, чтобы посчитать долговую нагрузку."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.incomeSources, key = { "income-${it.id}" }) { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    trailingContent = {
                        Row {
                            Text(Money.format(source.monthlyAmount), modifier = Modifier.padding(top = 12.dp))
                            IconButton(onClick = { viewModel.deleteIncome(source) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить доход")
                            }
                        }
                    },
                )
            }
            item {
                TextButton(onClick = { showAddIncome = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Добавить доход", modifier = Modifier.padding(start = 4.dp))
                }
            }

            item { SectionHeader("Мои кредиты") }
            if (!state.loading && state.loans.isEmpty()) {
                item {
                    Text(
                        "Кредитов пока нет. Загрузите график платежей или договор из банка в PDF, скриншот или фото, либо добавьте кредит вручную.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.loans, key = { it.loan.id }) { row ->
                LoanListItem(row, onClick = { onOpenLoan(row.loan.id) })
                HorizontalDivider()
            }
        }
    }

    if (showAddIncome) {
        AddIncomeDialog(
            onConfirm = { name, amount ->
                viewModel.addIncome(name, amount)
                showAddIncome = false
            },
            onDismiss = { showAddIncome = false },
        )
    }
}

@Composable
private fun BurdenCard(state: LoansUiState) {
    val burden = state.burden
    val zone = burden.zone
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = zone?.color?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Долговая нагрузка (ПДН)", style = MaterialTheme.typography.labelLarge)
            val percent = burden.percent
            if (percent == null || zone == null) {
                Text("—", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Платежи ${Money.format(burden.payments)} в месяц. Добавьте доход, чтобы увидеть ПДН.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            Text(
                "%.0f%%".format(percent),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = zone.color,
            )
            LinearProgressIndicator(
                progress = { (percent / 100).toFloat().coerceIn(0f, 1f) },
                color = zone.color,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(zone.title, style = MaterialTheme.typography.titleMedium, color = zone.color)
            Text(zone.hint, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Figure("Платежи в месяц", Money.format(burden.payments))
                Figure("Доход", Money.format(burden.income))
                Figure("Остаётся", Money.format(burden.freeIncome))
            }
            if (state.totalDebt > 0) {
                Text("Общий долг: ${Money.format(state.totalDebt)}", style = MaterialTheme.typography.bodyMedium)
            }
            state.nextChange?.let { change ->
                val names = change.closedLoans.joinToString(", ") { "«${it.name}»" }
                val next = change.burden.percent?.let { "%.0f%%".format(it) } ?: "—"
                val months = ChronoUnit.MONTHS.between(YearMonth.now(), change.month).toInt()
                Text(
                    "Через ${monthsTitle(months)} (${change.month.title()}) закроется $names, ПДН снизится до $next.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LoanListItem(row: LoanRow, onClick: () -> Unit) {
    val loan = row.loan
    ListItem(
        headlineContent = { Text(loan.name) },
        overlineContent = { Text(listOf(loan.type.label, loan.bank).filter { it.isNotBlank() }.joinToString(" · ")) },
        supportingContent = {
            val parts = buildList {
                if (loan.isCard) {
                    loan.creditLimit?.let { add("лимит ${Money.format(it)}") }
                    if (row.balance > 0) add("долг ${Money.format(row.balance)}")
                } else {
                    add("остаток ${Money.format(row.balance)}")
                    if (loan.annualRate > 0) add(formatRate(loan.annualRate))
                    row.endDate?.let { add("до ${it.shortTitle()}") }
                }
            }
            Text(parts.joinToString(" · "))
        },
        trailingContent = {
            Column {
                Text(Money.format(row.payment), style = MaterialTheme.typography.titleMedium)
                Text("в месяц", style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AddIncomeDialog(
    onConfirm: (String, Kopecks) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("Зарплата") }
    var amountText by remember { mutableStateOf("") }
    val amount = Money.parse(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доход в месяц") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Источник") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма в месяц после налогов, ₽") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(name.ifBlank { "Доход" }, it) } },
                enabled = amount != null,
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
