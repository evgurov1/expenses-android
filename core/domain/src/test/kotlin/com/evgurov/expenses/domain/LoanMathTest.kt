package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.usecase.BurdenZone
import com.evgurov.expenses.domain.usecase.IncomeBasis
import com.evgurov.expenses.domain.usecase.LoanMath
import com.evgurov.expenses.domain.usecase.burdenForecast
import com.evgurov.expenses.domain.usecase.debtBurden
import com.evgurov.expenses.domain.usecase.monthlyIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LoanMathTest {
    private val consumer = Loan(
        type = LoanType.CONSUMER,
        name = "Потребкредит",
        principal = 1_000_000_00,
        annualRate = 12.0,
        termMonths = 12,
        issueDate = LocalDate.of(2026, 1, 15),
    )

    @Test
    fun `annuity payment matches the textbook value`() {
        assertEquals(88_848_79L, LoanMath.annuityPayment(1_000_000_00, 12.0, 12))
    }

    @Test
    fun `zero rate splits principal evenly`() {
        assertEquals(10_000_00L, LoanMath.annuityPayment(120_000_00, 0.0, 12))
    }

    @Test
    fun `annuity schedule repays exactly the principal`() {
        val schedule = LoanMath.schedule(consumer)
        assertEquals(12, schedule.size)
        assertEquals(consumer.principal, schedule.sumOf { it.principalPart })
        assertEquals(0L, schedule.last().balanceAfter)
        assertEquals(LocalDate.of(2026, 2, 15), schedule.first().dueDate)
        schedule.dropLast(1).forEach { assertEquals(88_848_79L, it.payment) }
        assertTrue(kotlin.math.abs(schedule.last().payment - 88_848_79L) < 100)
    }

    @Test
    fun `differentiated schedule has falling payments`() {
        val schedule = LoanMath.schedule(consumer.copy(paymentType = PaymentType.DIFFERENTIATED))
        assertEquals(consumer.principal, schedule.sumOf { it.principalPart })
        assertTrue(schedule.zipWithNext().all { (a, b) -> a.payment > b.payment })
        assertEquals(83_333_33L + 10_000_00L, schedule.first().payment)
    }

    @Test
    fun `balance follows the schedule unless user set it`() {
        assertEquals(consumer.principal, LoanMath.balanceOn(consumer, LocalDate.of(2026, 2, 14)))
        assertEquals(0L, LoanMath.balanceOn(consumer, LocalDate.of(2027, 1, 15)))
        assertEquals(5_00L, LoanMath.balanceOn(consumer.copy(currentBalance = 5_00), LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `credit card counts as five percent of limit`() {
        val card = Loan(type = LoanType.CREDIT_CARD, name = "Карта", creditLimit = 200_000_00)
        assertEquals(10_000_00L, LoanMath.paymentInMonth(card, YearMonth.of(2026, 10)))
        assertTrue(LoanMath.schedule(card).isEmpty())
    }

    @Test
    fun `payment outside the term is zero`() {
        assertEquals(0L, LoanMath.paymentInMonth(consumer, YearMonth.of(2027, 2)))
        assertEquals(88_848_79L, LoanMath.paymentInMonth(consumer, YearMonth.of(2026, 6)))
    }

    @Test
    fun `contract payment is used for the burden`() {
        val loan = consumer.copy(monthlyPayment = 90_000_00)
        assertEquals(90_000_00L, LoanMath.paymentInMonth(loan, YearMonth.of(2026, 6)))
    }

    @Test
    fun `debt burden and zones`() {
        val card = Loan(type = LoanType.CREDIT_CARD, name = "Карта", creditLimit = 100_000_00)
        val burden = debtBurden(listOf(consumer, card), income = 200_000_00, month = YearMonth.of(2026, 6))
        assertEquals(88_848_79L + 5_000_00L, burden.payments)
        assertEquals(46.92, burden.percent!!, 0.01)
        assertEquals(BurdenZone.MODERATE, burden.zone)
        assertNull(debtBurden(listOf(consumer), 0, YearMonth.of(2026, 6)).percent)
    }

    @Test
    fun `income comes from sources, else from recent transactions`() {
        val now = YearMonth.of(2026, 10)
        val tx = listOf(
            Transaction(accountId = 1, categoryId = 1, amount = 90_000_00, kind = TransactionKind.INCOME, date = LocalDate.of(2026, 9, 5)),
            Transaction(accountId = 1, categoryId = 1, amount = 90_000_00, kind = TransactionKind.INCOME, date = LocalDate.of(2026, 8, 5)),
            Transaction(accountId = 1, categoryId = 1, amount = 90_000_00, kind = TransactionKind.INCOME, date = LocalDate.of(2026, 7, 5)),
            Transaction(accountId = 1, categoryId = 1, amount = 1_000_00, kind = TransactionKind.INCOME, date = LocalDate.of(2026, 10, 1)),
        )
        assertEquals(90_000_00L, monthlyIncome(emptyList(), tx, now).amount)
        assertEquals(IncomeBasis.TRANSACTIONS, monthlyIncome(emptyList(), tx, now).basis)
        val fromSources = monthlyIncome(listOf(IncomeSource(name = "Зарплата", monthlyAmount = 150_000_00)), tx, now)
        assertEquals(150_000_00L, fromSources.amount)
        assertEquals(IncomeBasis.NONE, monthlyIncome(emptyList(), emptyList(), now).basis)
    }

    @Test
    fun `forecast shows when a loan closes`() {
        val forecast = burdenForecast(listOf(consumer), 200_000_00, YearMonth.of(2026, 10))
        assertEquals(1, forecast.size)
        assertEquals(YearMonth.of(2027, 2), forecast.single().month)
        assertEquals(0L, forecast.single().burden.payments)
    }
}
