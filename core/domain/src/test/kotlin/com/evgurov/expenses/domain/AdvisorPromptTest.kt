package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.AdvisorPrompt
import com.evgurov.expenses.domain.usecase.ChatTurn
import com.evgurov.expenses.domain.usecase.PayoffPlanner
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
    fun `facts contain plan numbers and the prompt keeps last turns`() {
        val advice = PayoffPlanner.advise(listOf(loan), 10_000_00, income = 150_000_00, today = today)
        val facts = AdvisorPrompt.facts(listOf(loan), advice, 10_000_00, 150_000_00, today)
        assertTrue("«Кредит Ozon Банк», Ozon Банк: остаток ${Money.format(197_884_71)}" in facts)
        assertTrue("Если доплачивать ${Money.format(10_000_00)} в месяц" in facts)
        assertTrue("Советы приложения" in facts)
        val history = (1..5).map { ChatTurn("в$it", "о$it") }
        val prompt = AdvisorPrompt.build(facts, history, "Когда закрою?")
        assertTrue(prompt.startsWith("Ты — помощник"))
        assertTrue("Вопрос: в3" in prompt && "Вопрос: в2" !in prompt)
        assertTrue(prompt.endsWith("Вопрос: Когда закрою?\nОтвет:"))
    }
}
