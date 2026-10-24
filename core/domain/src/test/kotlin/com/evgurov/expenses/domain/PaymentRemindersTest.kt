package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.PaymentReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaymentRemindersTest {
    private val ozon = Loan(
        id = 1, type = LoanType.CONSUMER, name = "Кредит Ozon Банк", principal = 200_000_00, annualRate = 40.7,
        termMonths = 60, issueDate = LocalDate.of(2026, 8, 2), monthlyPayment = 7_844_00,
    )
    private val tbank = Loan(
        id = 2, type = LoanType.CONSUMER, name = "Кредит ТБанк", principal = 1_000_000_00, annualRate = 30.0,
        termMonths = 60, issueDate = LocalDate.of(2026, 5, 27), monthlyPayment = 32_000_00,
    )

    @Test
    fun `payday reminder lists payments paid from this salary`() {
        // 20 октября: с этой зарплаты — ТБанк 27 октября и Ozon 2 ноября.
        val reminders = PaymentReminders.forDay(listOf(ozon, tbank), listOf(5, 20), LocalDate.of(2026, 10, 20))
        val payday = reminders.single { it.id == 1 }
        assertEquals("Зарплата: отложите ${Money.format(39_844_00)} на кредиты", payday.title)
        assertTrue(payday.text, "до 27 октября" in payday.text && "до 2 ноября" in payday.text)
    }

    @Test
    fun `reminds two days before a debit`() {
        val reminders = PaymentReminders.forDay(listOf(ozon, tbank), emptyList(), LocalDate.of(2026, 10, 31))
        val soon = reminders.single()
        assertEquals("Через 2 дня списание ${Money.format(7_844_00)}", soon.title)
        assertTrue(PaymentReminders.forDay(listOf(ozon), listOf(5, 20), LocalDate.of(2026, 10, 25)).isEmpty())
    }
}
