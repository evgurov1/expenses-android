package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.ScheduleItem
import com.evgurov.expenses.ui.components.ConfirmDeleteDialog
import com.evgurov.expenses.ui.components.shortTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailsScreen(
    onBack: () -> Unit,
    viewModel: LoanDetailsViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(details?.loan?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить кредит")
                    }
                },
            )
        },
    ) { padding ->
        val d = details ?: return@Scaffold
        val loan = d.loan
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Line("Тип", loan.type.label)
                        if (loan.bank.isNotBlank()) Line("Банк", loan.bank)
                        if (loan.isCard) {
                            loan.creditLimit?.let { Line("Лимит", Money.format(it)) }
                            Line("Текущий долг", Money.format(d.balance))
                            Line("Платёж для ПДН (5% лимита)", Money.format(d.payment))
                        } else {
                            Line("Сумма кредита", Money.format(loan.principal))
                            Line("Ставка", formatRate(loan.annualRate))
                            Line("Срок", monthsTitle(loan.termMonths))
                            Line("Выдан", loan.issueDate.shortTitle())
                            Line("Платёж в месяц", Money.format(d.payment))
                            Line("Остаток долга", Money.format(d.balance))
                            Line("Осталось заплатить процентов", Money.format(d.remainingInterest))
                            d.schedule.lastOrNull()?.let { Line("Последний платёж", it.dueDate.shortTitle()) }
                        }
                    }
                }
            }
            if (d.schedule.isNotEmpty()) {
                item {
                    Text(
                        "График платежей",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                    Text(
                        "Расчётный: по сумме, ставке и сроку. Точные даты и суммы смотрите в графике банка.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(d.schedule, key = { it.number }) { item ->
                    ScheduleRow(item, paid = !item.dueDate.isAfter(d.today))
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Удалить кредит?",
            text = "Кредит пропадёт из списка и из расчёта ПДН.",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem, paid: Boolean) {
    val color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("${item.number}. ${item.dueDate.shortTitle()}", color = color)
            Text(
                "долг ${Money.format(item.principalPart)} · проценты ${Money.format(item.interestPart)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text(Money.format(item.payment), color = color)
            Text(
                "ост. ${Money.format(item.balanceAfter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
