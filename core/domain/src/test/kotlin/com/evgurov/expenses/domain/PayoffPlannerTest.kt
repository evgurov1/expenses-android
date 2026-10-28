package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.usecase.LoanMath
import com.evgurov.expenses.domain.usecase.PayoffPlanner
import com.evgurov.expenses.domain.usecase.PayoffStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PayoffPlannerTest {
    private val today = LocalDate.of(2026, 10, 24)

    private val big = Loan(
        id = 1, type = LoanType.CONSUMER, name = "Большой", principal = 1_000_000_00, annualRate = 20.0,
        termMonths = 60, issueDate = LocalDate.of(2026, 10, 1),
    )
    private val expensive = Loan(
        id = 2, type = LoanType.CONSUMER, name = "Дорогой", principal = 200_000_00, annualRate = 40.7,
        termMonths = 60, issueDate = LocalDate.of(2026, 10, 2), monthlyPayment = 7_844_00,
    )

    @Test
    fun `without extra payments the plan matches the schedules`() {
        val plan = PayoffPlanner.simulate(listOf(big), 0, 0, PayoffStrategy.AVALANCHE, rollover = false, today)
        assertEquals(YearMonth.of(2031, 10), plan.debtFreeMonth)
        val scheduleInterest = LoanMath.schedule(big).sumOf { it.interestPart }
        // Помесячная симуляция и график расходятся только на округлении копеек.
        assertTrue(kotlin.math.abs(plan.totalInterest - scheduleInterest) < 100_00)
    }

    @Test
    fun `avalanche sends extra money to the highest rate first`() {
        val advice = PayoffPlanner.advise(listOf(big, expensive), extraMonthly = 10_000_00, today = today)
        assertEquals("Дорогой", advice.order.first().name)
        val expensiveClose = advice.plan.loans.first { it.loan.name == "Дорогой" }.closeMonth
        assertNotNull(expensiveClose)
        assertTrue(expensiveClose!! < YearMonth.of(2028, 6))
        assertTrue(advice.savedInterest > 100_000_00)
        assertTrue(advice.monthsEarlier >= 12)
        assertTrue(advice.tips.any { "Дорогой" in it && "самая высокая ставка" in it })
        assertTrue(advice.tips.any { "Рефинансирование" in it && "Дорогой" in it })
    }

    @Test
    fun `avalanche never pays more interest than snowball`() {
        val loans = listOf(big, expensive)
        val avalanche = PayoffPlanner.advise(loans, 15_000_00, strategy = PayoffStrategy.AVALANCHE, today = today)
        val snowball = PayoffPlanner.advise(loans, 15_000_00, strategy = PayoffStrategy.SNOWBALL, today = today)
        assertTrue(avalanche.plan.totalInterest <= snowball.plan.totalInterest)
    }

    @Test
    fun `lump sum closes a small loan right away`() {
        val advice = PayoffPlanner.advise(listOf(expensive), extraMonthly = 0, lumpSum = 300_000_00, today = today)
        assertEquals(YearMonth.of(2026, 11), advice.plan.debtFreeMonth)
    }

    @Test
    fun `card tip mentions five percent of limit`() {
        val card = Loan(type = LoanType.CREDIT_CARD, name = "Карта", annualRate = 30.0, creditLimit = 100_000_00, currentBalance = 20_000_00)
        val advice = PayoffPlanner.advise(listOf(card), extraMonthly = 5_000_00, today = today)
        assertNotNull(advice.plan.debtFreeMonth)
        assertTrue(advice.tips.any { "5% лимита" in it })
    }

    @Test
    fun `no loans gives empty advice`() {
        val advice = PayoffPlanner.advise(emptyList(), 10_000_00, today = today)
        assertTrue(advice.tips.isEmpty())
        assertEquals(0L, advice.savedInterest)
    }
}
