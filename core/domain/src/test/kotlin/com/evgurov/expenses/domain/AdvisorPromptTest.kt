package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.AdvisorPrompt
import com.evgurov.expenses.domain.usecase.ChatTurn
import com.evgurov.expenses.domain.usecase.PayoffPlanner
import com.evgurov.expenses.domain.usecase.PaydaySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdvisorPromptTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val loan = Loan(
        id = 1, type = LoanType.CONSUMER, name = "Кредит Ozon Банк", bank = "Ozon Банк", principal = 200_000_00,
        annualRate = 40.7, termMonths = 60, issueDate = LocalDate.of(2026, 8, 2), monthlyPayment = 7_844_00,
        currentBalance = 197_884_71,
    )

    @Test
    fun `reads extra amount from question`() {
        assertEquals(15_000_00L, AdvisorPrompt.extraFromQuestion("Что если вносить 15 000 в месяц?"))
        assertEquals(20_000_00L, AdvisorPrompt.extraFromQuestion("а если доплачивать 20к"))
        assertEquals(7_000_00L, AdvisorPrompt.extraFromQuestion("смогу платить ещё 7 тыс в месяц"))
        assertNull(AdvisorPrompt.extraFromQuestion("Какой кредит гасить первым?"))
        assertNull(AdvisorPrompt.extraFromQuestion("Стоит ли рефинансировать под 20%?"))
    }

    @Test
    fun `facts contain plan numbers and the prompt keeps only last questions`() {
        val advice = PayoffPlanner.advise(listOf(loan), 10_000_00, income = 150_000_00, today = today)
        val facts = AdvisorPrompt.facts(listOf(loan), advice, 10_000_00, 150_000_00, today)
        assertTrue("«Кредит Ozon Банк», Ozon Банк: остаток ${Money.format(197_884_71)}" in facts)
        assertTrue("Если доплачивать ${Money.format(10_000_00)} в месяц" in facts)
        assertTrue("Советы приложения" in facts)
        val history = (1..5).map { ChatTurn("в$it", "о$it") }
        val prompt = AdvisorPrompt.build(facts, history, "Когда закрою?")
        assertTrue(prompt.startsWith("Ты — помощник"))
        assertTrue("«в3»" in prompt && "«в2»" !in prompt)
        assertTrue("о5" !in prompt)
        assertTrue(prompt.endsWith("Вопрос: Когда закрою?\nОтвет:"))
    }

    @Test
    fun `loans with the same name are told apart`() {
        val vtb1 = loan.copy(id = 2, name = "Кредит ВТБ", bank = "ВТБ", currentBalance = 100_000_00, annualRate = 20.0)
        val vtb2 = loan.copy(id = 3, name = "Кредит ВТБ", bank = "ВТБ", currentBalance = 300_000_00, annualRate = 18.0)
        val loans = listOf(loan, vtb1, vtb2)
        val advice = PayoffPlanner.advise(loans, 10_000_00, today = today)
        val facts = AdvisorPrompt.facts(loans, advice, 10_000_00, 0, today)
        assertTrue("«Кредит ВТБ» (остаток ${Money.format(100_000_00)})" in facts)
        assertTrue("«Кредит ВТБ» (остаток ${Money.format(300_000_00)})" in facts)
        assertTrue("«Кредит Ozon Банк», Ozon Банк" in facts)
        assertTrue("с доплатой" in facts)
    }

    @Test
    fun `recognises schedule requests and paydays`() {
        val q = "сделай мне график, я получаю зарплату 5 и 20 числа месяца"
        assertTrue(AdvisorPrompt.asksForSchedule(q))
        assertTrue(AdvisorPrompt.asksForSchedule("график то сделай мне"))
        assertTrue(!AdvisorPrompt.asksForSchedule("все или только озон закрою за 13 месяцев"))
        assertEquals(listOf(5, 20), PaydaySchedule.paydaysFromText(q))
        assertEquals(listOf(10, 25), PaydaySchedule.paydaysFromText("аванс 25-го, зарплата 10 числа"))
        assertEquals(emptyList<Int>(), PaydaySchedule.paydaysFromText("зарплата 25 000"))
    }

    @Test
    fun `payments go to the last payday before the debit date`() {
        val vtb = Loan(
            id = 2, type = LoanType.CONSUMER, name = "Кредит ВТБ", principal = 300_000_00, annualRate = 20.0,
            termMonths = 36, issueDate = LocalDate.of(2026, 3, 18),
        )
        val loans = listOf(loan, vtb)
        val advice = PayoffPlanner.advise(loans, 10_000_00, today = today)
        val schedule = PaydaySchedule.build(advice.plan, listOf(5, 20), today)
        // Ozon списывает 2 ноября: зарплата 20 октября уже пришла — откладываем из того, что есть.
        val now = schedule.first()
        assertEquals(today, now.date)
        assertTrue(now.alreadyPaid)
        assertEquals(LocalDate.of(2026, 11, 2), now.items.single().dueDate)
        // ВТБ списывает 18 ноября — платим с зарплаты 5 ноября, туда же половина досрочки.
        val nov5 = schedule.first { it.date == LocalDate.of(2026, 11, 5) }
        assertEquals(LocalDate.of(2026, 11, 18), nov5.items.first { it.dueDate != null }.dueDate)
        assertEquals(5_000_00L, nov5.items.filter { it.dueDate == null }.sumOf { it.amount })
        assertEquals(5_000_00L, schedule.first { it.date == LocalDate.of(2026, 11, 20) }.items.filter { it.dueDate == null }.sumOf { it.amount })
        val text = PaydaySchedule.describe(schedule, listOf(5, 20), loans, 10_000_00, advice.order.first(), today)
        assertTrue(text.startsWith("График по зарплатам 5 и 20 числа"))
        assertTrue("5 ноября, с зарплаты" in text)
    }
}
