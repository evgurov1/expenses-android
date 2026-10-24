package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.YearMonth

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

/** Текущий баланс каждого счёта: начальный остаток плюс все операции по нему. */
fun accountBalances(accounts: List<Account>, transactions: List<Transaction>): Map<Long, Kopecks> {
    val movement = transactions.groupBy { it.accountId }.mapValues { (_, list) -> list.sumOf { it.signedAmount } }
    return accounts.associate { it.id to it.initialBalance + (movement[it.id] ?: 0L) }
}
