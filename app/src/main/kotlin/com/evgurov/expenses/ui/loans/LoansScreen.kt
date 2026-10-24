package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.BurdenZone
import com.evgurov.expenses.domain.usecase.IncomeBasis
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.SectionTitle
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
    onPayoff: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddIncome by remember { mutableStateOf(false) }
    var showPaydays by remember { mutableStateOf(false) }
    val paydays by viewModel.paydays.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Кредиты", style = MaterialTheme.typography.headlineMedium) })
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onAddLoan,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Вручную") },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp, 2.dp, 2.dp, 2.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = onImportLoan,
                    icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                    text = { Text("Загрузить документы") },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp, 4.dp, 4.dp, 4.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BurdenCard(state) }
            if (state.totalDebt > 0) {
                item { PayoffEntry(onPayoff) }
            }

            item { SectionTitle("Доход в месяц", Modifier.padding(top = 12.dp, start = 4.dp)) }
            item {
                AppCard(contentPadding = 0.dp) {
                    if (state.incomeSources.isEmpty()) {
                        Text(
                            text = when (state.incomeBasis) {
                                IncomeBasis.TRANSACTIONS ->
                                    "Считаем по доходам из операций за 3 месяца: ${Money.format(state.burden.income)}. " +
                                        "Укажите доход явно, чтобы ПДН была точнее."
                                else -> "Укажите доход, чтобы посчитать долговую нагрузку."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                        )
                    }
                    state.incomeSources.forEachIndexed { index, source ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(source.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(Money.format(source.monthlyAmount), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { viewModel.deleteIncome(source) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Удалить доход",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { showAddIncome = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Добавить доход", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item {
                AppCard(onClick = { showPaydays = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Дни зарплаты", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (paydays.isEmpty()) "Укажите — напомню, сколько отложить на кредиты"
                                else "${paydays.joinToString(" и ")} числа · в эти дни напомню, сколько отложить",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Изменить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            item { SectionTitle("Мои кредиты", Modifier.padding(top = 12.dp, start = 4.dp)) }
            if (!state.loading && state.loans.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            "Кредитов пока нет. Загрузите график платежей, договор, скриншот или фото из банка — " +
                                "или добавьте кредит вручную.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.loans, key = { it.loan.id }) { row ->
                LoanCard(row, onClick = { onOpenLoan(row.loan.id) })
            }
        }
    }

    if (showPaydays) {
        PaydaysDialog(
            initial = paydays,
            onConfirm = {
                viewModel.setPaydays(it)
                showPaydays = false
            },
            onDismiss = { showPaydays = false },
        )
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
    AppCard(contentPadding = 24.dp) {
        Text(
            "Долговая нагрузка",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val percent = burden.percent
        if (percent == null || zone == null) {
            Text("—", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Платежи ${Money.format(burden.payments)} в месяц. Добавьте доход, чтобы увидеть ПДН.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
            Text("%.0f%%".format(percent), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            ZoneTag(zone)
        }
        LinearProgressIndicator(
            progress = { (percent / 100).toFloat().coerceIn(0f, 1f) },
            color = zone.color,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Text(
            zone.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Платежи в месяц", Money.format(burden.payments))
            Figure("Доход", Money.format(burden.income))
            Figure("Остаётся", Money.format(burden.freeIncome))
        }
        if (state.totalDebt > 0) {
            Text(
                "Общий долг ${Money.format(state.totalDebt)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        state.nextChange?.let { change ->
            val names = change.closedLoans.joinToString(", ") { "«${it.name}»" }
            val next = change.burden.percent?.let { "%.0f%%".format(it) } ?: "—"
            val months = ChronoUnit.MONTHS.between(YearMonth.now(), change.month).toInt()
            Text(
                "Через ${monthsTitle(months)} (${change.month.title()}) закроется $names, ПДН снизится до $next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PayoffEntry(onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Как закрыть быстрее", style = MaterialTheme.typography.titleMedium)
                Text(
                    "План досрочного погашения и советы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Метка-таблетка с цветной точкой: цвет здесь несёт смысл (зона ПДН). */
@Composable
private fun ZoneTag(zone: BurdenZone) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(8.dp).background(zone.color, CircleShape))
        Text(zone.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LoanCard(row: LoanRow, onClick: () -> Unit) {
    val loan = row.loan
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(loan.type.label, loan.bank).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(loan.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(row.payment), style = MaterialTheme.typography.titleMedium)
                Text("в месяц", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
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
                    colors = appFieldColors(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Источник") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    colors = appFieldColors(),
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

@Composable
private fun PaydaysDialog(
    initial: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString(", ")) }
    val days = Regex("""\d{1,2}""").findAll(text).mapNotNull { it.value.toIntOrNull() }.filter { it in 1..31 }.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Дни зарплаты") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Числа месяца, когда приходят зарплата и аванс. В эти дни придёт напоминание, сколько отложить на кредиты.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Например: 5, 20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(days) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
