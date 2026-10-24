package com.evgurov.expenses.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.evgurov.expenses.bank.BankNotificationService
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.usecase.PeriodSummary
import com.evgurov.expenses.ui.components.ConfirmDeleteDialog
import com.evgurov.expenses.ui.components.SignedAmount
import com.evgurov.expenses.ui.components.dayTitle
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.ui.accounts.AccountWithBalance
import com.evgurov.expenses.ui.accounts.BalanceDialog
import com.evgurov.expenses.ui.components.RussianLocale
import com.evgurov.expenses.ui.components.title
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.evgurov.expenses.ui.theme.IncomeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAddClick: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skipped by viewModel.skipped.collectAsStateWithLifecycle()
    val statementStatus by viewModel.statementStatus.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var editingBalance by remember { mutableStateOf<AccountWithBalance?>(null) }
    val pickStatement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importStatement)
    }
    var toDelete by remember { mutableStateOf<Transaction?>(null) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    val context = LocalContext.current
    // Доступ к уведомлениям включают в настройках системы — проверяем каждый раз, когда экран снова виден.
    var bankEnabled by remember { mutableStateOf(BankNotificationService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        bankEnabled = BankNotificationService.isEnabled(context)
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.period.title()) },
                navigationIcon = {
                    IconButton(onClick = viewModel::previousPeriod) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий период")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::nextPeriod) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий период")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить операцию")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item { MoneyNowCard(overview, onAccount = { editingBalance = it }) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                ) {
                    PeriodMode.entries.forEach { mode ->
                        PillChip(state.period.mode == mode, { viewModel.setPeriodMode(mode) }, mode.label)
                    }
                }
            }
            item { SummaryCard(state.summary) }
            item {
                CategoryChart(
                    kind = state.chartKind,
                    slices = state.slices,
                    selected = state.selected,
                    onKind = viewModel::setChartKind,
                    onSlice = viewModel::toggleSlice,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            item { BankCard(
                    enabled = bankEnabled,
                    skipped = skipped,
                    statementStatus = statementStatus,
                    onPickStatement = { pickStatement.launch(arrayOf("application/pdf")) },
                    onClearSkipped = viewModel::clearSkipped,
                ) }
            if (!state.loading && state.days.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "За этот период операций нет.\nНажмите «+», чтобы добавить.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = day.date.dayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(day.rows, key = { it.transaction.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.categoryName) },
                        supportingContent = {
                            val details = listOf(row.accountName, row.transaction.note).filter { it.isNotBlank() }
                            Text(details.joinToString(" · "))
                        },
                        trailingContent = { SignedAmount(row.transaction.amount, row.transaction.kind) },
                        modifier = Modifier.clickable { selected = row.transaction },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { transaction ->
        CategoryDialog(
            transaction = transaction,
            categories = state.categories.filter { it.kind == transaction.kind },
            onPick = { id ->
                viewModel.changeCategory(transaction, id)
                selected = null
            },
            onDelete = {
                selected = null
                toDelete = transaction
            },
            onDismiss = { selected = null },
        )
    }

    editingBalance?.let { item ->
        BalanceDialog(
            account = item.account,
            current = item.balance,
            onSave = {
                viewModel.setBalance(item.account, it)
                editingBalance = null
            },
            onDismiss = { editingBalance = null },
        )
    }

    toDelete?.let { transaction ->
        ConfirmDeleteDialog(
            title = "Удалить операцию?",
            text = "${if (transaction.kind == TransactionKind.INCOME) "Доход" else "Расход"} " +
                "на ${Money.format(transaction.amount)} будет удалён.",
            onConfirm = {
                viewModel.delete(transaction)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun SummaryCard(summary: PeriodSummary) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryValue("Доходы", Money.format(summary.income), IncomeColor)
            SummaryValue("Расходы", Money.format(summary.expense), MaterialTheme.colorScheme.onSurface)
            SummaryValue("Итог", Money.format(summary.net), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

private val weekDay = DateTimeFormatter.ofPattern("d MMMM", RussianLocale)

/** «Октябрь», «19 – 25 октября», «2026». */
private fun Period.title(): String = when (mode) {
    PeriodMode.WEEK -> if (from.month == to.month) "${from.dayOfMonth} – ${to.format(weekDay)}" else "${from.format(weekDay)} – ${to.format(weekDay)}"
    PeriodMode.MONTH -> YearMonth.from(anchor).title()
    PeriodMode.YEAR -> anchor.year.toString()
}
