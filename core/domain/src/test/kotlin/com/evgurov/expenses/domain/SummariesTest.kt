package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind.EXPENSE
import com.evgurov.expenses.domain.model.TransactionKind.INCOME
import com.evgurov.expenses.domain.usecase.accountBalances
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

    @Test(expected = IllegalArgumentException::class)
    fun `transaction amount must be positive`() {
        Transaction(accountId = 1, categoryId = 1, amount = 0, kind = EXPENSE, date = LocalDate.now())
    }
}
