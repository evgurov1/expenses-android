package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind.EXPENSE
import com.evgurov.expenses.domain.model.TransactionKind.INCOME
import com.evgurov.expenses.domain.usecase.accountBalances
import com.evgurov.expenses.domain.usecase.forecastMonthSpending
import com.evgurov.expenses.domain.usecase.withBalanceNow
import com.evgurov.expenses.domain.usecase.summarizeMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SummariesTest {
    private val card = Account(id = 1, name = "Карта", type = AccountType.CARD, initialBalance = 10_000_00)
    private val cash = Account(id = 2, name = "Наличные", type = AccountType.CASH)

    private val transactions = listOf(
        Transaction(accountId = 1, categoryId = 1, amount = 80_000_00, kind = INCOME, date = LocalDate.of(2026, 10, 5)),
        Transaction(accountId = 1, categoryId = 2, amount = 3_500_50, kind = EXPENSE, date = LocalDate.of(2026, 10, 7)),
        Transaction(accountId = 2, categoryId = 2, amount = 500_00, kind = EXPENSE, date = LocalDate.of(2026, 10, 31)),
        Transaction(accountId = 1, categoryId = 2, amount = 1_000_00, kind = EXPENSE, date = LocalDate.of(2026, 9, 30)),
    )

    @Test
    fun `month summary counts only that month`() {
        val summary = summarizeMonth(transactions, YearMonth.of(2026, 10))
        assertEquals(80_000_00L, summary.income)
        assertEquals(4_000_50L, summary.expense)
        assertEquals(75_999_50L, summary.net)
    }

    @Test
    fun `account balance includes initial balance and all movements`() {
        val balances = accountBalances(listOf(card, cash), transactions)
        assertEquals(10_000_00L + 80_000_00 - 3_500_50 - 1_000_00, balances[1])
        assertEquals(-500_00L, balances[2])
    }

    @Test
    fun `known balance counts only later movements`() {
        val known = card.copy(knownBalance = 50_000_00, knownBalanceDate = LocalDate.of(2026, 10, 5))
        val balances = accountBalances(listOf(known), transactions)
        assertEquals(50_000_00L - 3_500_50, balances[1])
    }

    @Test
    fun `forecast adds average daily spending for days without data`() {
        // 30 дней по 1 000 ₽ с 1 по 30 сентября и с 1 по 10 октября, плюс разовый платёж по кредиту.
        val days = (0L until 40L).map { LocalDate.of(2026, 9, 1).plusDays(it) }
        val list = days.map { Transaction(accountId = 1, categoryId = 2, amount = 1_000_00, kind = EXPENSE, date = it) } +
            Transaction(accountId = 1, categoryId = 9, amount = 70_000_00, kind = EXPENSE, date = LocalDate.of(2026, 10, 3))
        val f = forecastMonthSpending(list, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 15), setOf(9L))!!
        assertEquals(1_000_00L, f.dailyRate)
        assertEquals(80_000_00L, f.spent)
        // С 11 по 31 октября ещё 21 день.
        assertEquals(80_000_00L + 21 * 1_000_00L, f.forecast)
        assertEquals(null, forecastMonthSpending(list, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `balance entered now still changes with later operations of the day`() {
        val day = LocalDate.of(2026, 10, 7)
        val known = card.withBalanceNow(20_000_00, day, transactions)
        assertEquals(20_000_00L, accountBalances(listOf(known), transactions)[1])
        val later = transactions + Transaction(accountId = 1, categoryId = 2, amount = 100_00, kind = EXPENSE, date = day)
        assertEquals(19_900_00L, accountBalances(listOf(known), later)[1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transaction amount must be positive`() {
        Transaction(accountId = 1, categoryId = 1, amount = 0, kind = EXPENSE, date = LocalDate.now())
    }
}
