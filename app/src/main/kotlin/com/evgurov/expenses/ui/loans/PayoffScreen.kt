package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.LoanMath
import com.evgurov.expenses.domain.usecase.PayoffAdvice
import com.evgurov.expenses.domain.usecase.PayoffStrategy
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.ui.components.SectionTitle
import com.evgurov.expenses.ui.components.appFieldColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoffScreen(
    onBack: () -> Unit,
    viewModel: PayoffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input = state.input

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Как закрыть быстрее") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(
                        "Сколько готовы добавлять к обязательным платежам",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        OutlinedTextField(
                            colors = appFieldColors(),
                            value = input.extraText,
                            onValueChange = { v -> viewModel.edit { it.copy(extraText = v) } },
                            label = { Text("В месяц, ₽") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            colors = appFieldColors(),
                            value = input.lumpText,
                            onValueChange = { v -> viewModel.edit { it.copy(lumpText = v) } },
                            label = { Text("Разово, ₽") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        PayoffStrategy.entries.forEach { strategy ->
                            PillChip(
                                selected = input.strategy == strategy,
                                onClick = { viewModel.edit { it.copy(strategy = strategy) } },
                                label = strategy.label,
                            )
                        }
                    }
                    Text(
                        input.strategy.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            val advice = state.advice
            if (!state.hasLoans || advice == null) {
                item {
                    AppCard {
                        Text(
                            "Нет кредитов с остатком долга. Добавьте кредит, и здесь появится план погашения.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@LazyColumn
            }

            item { SummaryCard(advice) }

            item { SectionTitle("Порядок погашения", Modifier.padding(start = 4.dp, top = 12.dp)) }
            item { OrderCard(advice) }

            item { SectionTitle("Советы", Modifier.padding(start = 4.dp, top = 12.dp)) }
            items(advice.tips) { tip ->
                AppCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            item {
                Text(
                    "Расчёт приблизительный: проценты считаются по ставке и графику банка, без страховок и комиссий. " +
                        "Перед досрочным платежом уточните сумму в приложении банка.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(advice: PayoffAdvice) {
    AppCard(contentPadding = 24.dp) {
        val withExtra = advice.savedInterest > 0
        Text(
            if (withExtra) "Сэкономите на процентах" else "Переплата по процентам",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money.format(if (withExtra) advice.savedInterest else advice.plan.totalInterest),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Без долгов", advice.plan.debtFreeMonth?.let(::monthYear) ?: "—")
            if (withExtra) Figure("Вместо", advice.baseline.debtFreeMonth?.let(::monthYear) ?: "—")
            if (advice.monthsEarlier > 0) Figure("Раньше на", monthsTitle(advice.monthsEarlier))
        }
        if (withExtra) {
            Text(
                "Переплата по процентам: ${Money.format(advice.plan.totalInterest)} вместо ${Money.format(advice.baseline.totalInterest)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun OrderCard(advice: PayoffAdvice) {
    val today = LocalDate.now()
    AppCard(contentPadding = 0.dp) {
        advice.order.forEachIndexed { index, loan ->
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            val planned = advice.plan.loans.firstOrNull { it.loan.id == loan.id && it.loan.name == loan.name }?.closeMonth
            val before = advice.baseline.loans.firstOrNull { it.loan.id == loan.id && it.loan.name == loan.name }?.closeMonth
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            CircleShape,
                        ),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(loan.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${formatRate(loan.annualRate)} · остаток ${Money.format(LoanMath.balanceOn(loan, today))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(planned?.let(::monthYear) ?: "—", style = MaterialTheme.typography.titleSmall)
                    if (before != null && planned != null && planned < before) {
                        Text(
                            "вместо ${monthYear(before)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

private val PayoffStrategy.label: String
    get() = when (this) {
        PayoffStrategy.AVALANCHE -> "Сначала дорогие"
        PayoffStrategy.SNOWBALL -> "Сначала маленькие"
    }

private val PayoffStrategy.hint: String
    get() = when (this) {
        PayoffStrategy.AVALANCHE ->
            "«Лавина»: досрочка идёт в кредит с самой высокой ставкой. Меньше всего переплата."
        PayoffStrategy.SNOWBALL ->
            "«Снежный ком»: сначала закрываете самый маленький кредит, его платёж добавляете к следующему. Быстрее падает ПДН."
    }

private fun monthYear(month: YearMonth): String {
    val name = month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.forLanguageTag("ru-RU")).trimEnd('.')
    return "$name ${month.year}"
}
