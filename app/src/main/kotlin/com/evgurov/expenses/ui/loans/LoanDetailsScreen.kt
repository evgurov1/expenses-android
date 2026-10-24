package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.ScheduleItem
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.ConfirmDeleteDialog
import com.evgurov.expenses.ui.components.SectionTitle
import com.evgurov.expenses.ui.components.shortTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailsScreen(
    onBack: () -> Unit,
    viewModel: LoanDetailsViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var addEarly by remember { mutableStateOf(false) }

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
        val regular = d.schedule.filterNot { it.early }
        val paidCount = regular.count { !it.dueDate.isAfter(d.today) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(contentPadding = 24.dp) {
                    Text(
                        if (loan.isCard) "Текущий долг" else "Осталось выплатить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Money.format(d.balance),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (!loan.isCard && regular.isNotEmpty()) {
                        val left = regular.size - paidCount
                        LinearProgressIndicator(
                            progress = { paidCount.toFloat() / regular.size },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.secondaryContainer,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(6.dp),
                        )
                        Text(
                            "Ещё ${paymentsTitle(left)} из ${regular.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    regular.firstOrNull { it.dueDate.isAfter(d.today) }?.let { next ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Следующий платёж",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(next.dueDate.shortTitle(), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(Money.format(next.payment), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { addEarly = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (loan.isCard) "Внести погашение" else "Внести досрочный платёж")
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Line("Тип", loan.type.label)
                        if (loan.bank.isNotBlank()) Line("Банк", loan.bank)
                        if (loan.note.isNotBlank()) Line("Договор", loan.note.removePrefix("Договор "))
                        if (loan.isCard) {
                            loan.creditLimit?.let { Line("Лимит", Money.format(it)) }
                            Line("Платёж для ПДН (5% лимита)", Money.format(d.payment))
                        } else {
                            Line("Сумма кредита", Money.format(loan.principal))
                            Line("Ставка", formatRate(loan.annualRate))
                            Line("Срок", monthsTitle(loan.termMonths))
                            Line("Выдан", loan.issueDate.shortTitle())
                            Line("Платёж в месяц", Money.format(d.payment))
                            Line("Осталось процентов", Money.format(d.remainingInterest))
                            d.schedule.lastOrNull()?.let { Line("Последний платёж", it.dueDate.shortTitle()) }
                        }
                    }
                }
            }
            if (d.schedule.isNotEmpty()) {
                item {
                    SectionTitle("График платежей", Modifier.padding(start = 4.dp, top = 12.dp))
                    Text(
                        if (d.schedule.any { it.early }) {
                            "Пересчитан приложением после досрочного платежа. Точный график смотрите в приложении банка."
                        } else if (loan.bankSchedule.isNotEmpty()) {
                            "Из документа банка."
                        } else {
                            "Расчётный: по сумме, ставке и сроку. Точные даты и суммы смотрите в графике банка."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                item {
                    AppCard(contentPadding = 0.dp) {
                        d.schedule.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            ScheduleRow(item, paid = !item.dueDate.isAfter(d.today))
                        }
                    }
                }
            }
        }
    }

    if (addEarly) {
        details?.loan?.let { loan ->
            EarlyPaymentDialog(
                loan = loan,
                onSave = { result ->
                    addEarly = false
                    viewModel.save(result.loan)
                },
                onDismiss = { addEarly = false },
            )
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun paymentsTitle(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "платежей"
        count % 10 == 1 -> "платёж"
        count % 10 in 2..4 -> "платежа"
        else -> "платежей"
    }
    return "$count $word"
}

@Composable
private fun ScheduleRow(item: ScheduleItem, paid: Boolean) {
    val color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("${item.number}. ${item.dueDate.shortTitle()}", color = color)
            Text(
                if (item.early) "досрочный платёж" else "долг ${Money.format(item.principalPart)} · проценты ${Money.format(item.interestPart)}",
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
