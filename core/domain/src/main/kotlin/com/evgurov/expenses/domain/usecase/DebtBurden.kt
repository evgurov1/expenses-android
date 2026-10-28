package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.YearMonth

/** Зоны ПДН по порогам, которые использует Банк России. */
enum class BurdenZone { LOW, MODERATE, HIGH, CRITICAL }

data class DebtBurden(
    val payments: Kopecks,
    val income: Kopecks,
) {
    /** ПДН в процентах или null, если доход неизвестен. */
    val percent: Double? get() = if (income > 0) payments * 100.0 / income else null

    val zone: BurdenZone? get() = percent?.let {
        when {
            it <= 30.0 -> BurdenZone.LOW
            it <= 50.0 -> BurdenZone.MODERATE
            it <= 80.0 -> BurdenZone.HIGH
            else -> BurdenZone.CRITICAL
        }
    }

    /** Сколько остаётся после платежей по кредитам. */
    val freeIncome: Kopecks get() = income - payments
}

enum class IncomeBasis { SOURCES, TRANSACTIONS, NONE }

data class MonthlyIncome(val amount: Kopecks, val basis: IncomeBasis)

/**
 * Среднемесячный доход: сумма источников дохода, а если их нет —
 * среднее по доходным операциям за последние [months] полных месяцев.
 */
fun monthlyIncome(
    sources: List<IncomeSource>,
    transactions: List<Transaction>,
    current: YearMonth,
    months: Int = 3,
): MonthlyIncome {
    if (sources.isNotEmpty()) return MonthlyIncome(sources.sumOf { it.monthlyAmount }, IncomeBasis.SOURCES)
    val window = (1..months).map { current.minusMonths(it.toLong()) }.toSet()
    val total = transactions
        .filter { it.kind == TransactionKind.INCOME && YearMonth.from(it.date) in window }
        .sumOf { it.amount }
    return if (total > 0) MonthlyIncome(total / months, IncomeBasis.TRANSACTIONS) else MonthlyIncome(0, IncomeBasis.NONE)
}

fun debtBurden(loans: List<Loan>, income: Kopecks, month: YearMonth): DebtBurden =
    DebtBurden(payments = loans.sumOf { LoanMath.paymentInMonth(it, month) }, income = income)

data class BurdenChange(val month: YearMonth, val closedLoans: List<Loan>, val burden: DebtBurden)

/** Месяцы в ближайшие [horizon] месяцев, когда закрываются кредиты и ПДН снижается. */
fun burdenForecast(loans: List<Loan>, income: Kopecks, from: YearMonth, horizon: Int = 24): List<BurdenChange> {
    val changes = mutableListOf<BurdenChange>()
    for (i in 1..horizon) {
        val month = from.plusMonths(i.toLong())
        val closed = loans.filter { loan ->
            !loan.isCard && LoanMath.endDate(loan)?.let { YearMonth.from(it) == month.minusMonths(1) } == true
        }
        if (closed.isNotEmpty()) changes += BurdenChange(month, closed, debtBurden(loans, income, month))
    }
    return changes
}
