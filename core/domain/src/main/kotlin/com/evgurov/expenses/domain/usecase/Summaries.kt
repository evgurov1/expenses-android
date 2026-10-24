package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class PeriodSummary(
    val income: Kopecks,
    val expense: Kopecks,
) {
    val net: Kopecks get() = income - expense
}

fun summarize(transactions: List<Transaction>): PeriodSummary = PeriodSummary(
    income = transactions.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amount },
    expense = transactions.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amount },
)

fun summarizeMonth(transactions: List<Transaction>, month: YearMonth): PeriodSummary =
    summarize(transactions.filter { YearMonth.from(it.date) == month })

/** Прогноз трат за месяц: уже потрачено, ожидаемый итог и средние траты в день. */
data class SpendingForecast(val spent: Kopecks, val forecast: Kopecks, val dailyRate: Kopecks)

/**
 * Приблизительный прогноз трат за текущий [month]: то, что уже потрачено, плюс средние траты в день
 * за последние 90 дней с операциями, умноженные на дни, за которые операций ещё нет.
 * Траты из [irregularCategoryIds] (платежи по кредитам) в среднее не входят: они идут по графику, а не каждый день.
 * Null — если месяц не текущий или данных меньше двух недель.
 */
fun forecastMonthSpending(
    transactions: List<Transaction>,
    month: YearMonth,
    today: LocalDate,
    irregularCategoryIds: Set<Long> = emptySet(),
): SpendingForecast? {
    if (YearMonth.from(today) != month) return null
    val expenses = transactions.filter { it.kind == TransactionKind.EXPENSE && it.date <= today }
    if (expenses.isEmpty()) return null
    val lastDay = expenses.maxOf { it.date }
    val firstDay = maxOf(expenses.minOf { it.date }, lastDay.minusDays(89))
    val days = ChronoUnit.DAYS.between(firstDay, lastDay) + 1
    if (days < 14) return null
    val rate = expenses
        .filter { it.date >= firstDay && it.categoryId !in irregularCategoryIds }
        .sumOf { it.amount } / days
    val spent = expenses.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
    // Дни после последней известной операции: выписка могла закончиться раньше, чем сегодня.
    val coveredUntil = maxOf(lastDay, month.atDay(1).minusDays(1))
    val remaining = ChronoUnit.DAYS.between(coveredUntil, month.atEndOfMonth()).coerceAtLeast(0)
    return SpendingForecast(spent, spent + rate * remaining, rate)
}

/**
 * Запоминает остаток «сейчас» в день [date]. Храним его на конец предыдущего дня за вычетом уже записанных
 * сегодняшних операций — тогда операции, которые появятся позже в тот же день, тоже изменят остаток.
 */
fun Account.withBalanceNow(amount: Kopecks, date: LocalDate, transactions: List<Transaction>): Account {
    val today = transactions.filter { it.accountId == id && it.date == date }.sumOf { it.signedAmount }
    return copy(knownBalance = amount - today, knownBalanceDate = date.minusDays(1))
}

/** Текущий баланс каждого счёта: начальный остаток плюс все операции по нему. */
fun accountBalances(accounts: List<Account>, transactions: List<Transaction>): Map<Long, Kopecks> {
    val byAccount = transactions.groupBy { it.accountId }
    return accounts.associate { account ->
        val list = byAccount[account.id].orEmpty()
        val known = account.knownBalance
        val date = account.knownBalanceDate
        // Известный остаток плюс всё, что случилось после дня, на который он известен.
        account.id to if (known != null && date != null) {
            known + list.filter { it.date > date }.sumOf { it.signedAmount }
        } else {
            account.initialBalance + list.sumOf { it.signedAmount }
        }
    }
}
