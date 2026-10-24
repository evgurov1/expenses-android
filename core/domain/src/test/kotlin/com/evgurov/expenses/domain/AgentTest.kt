package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.assistant.AgentMessage
import com.evgurov.expenses.domain.assistant.AgentPrompt
import com.evgurov.expenses.domain.assistant.LoanTools
import com.evgurov.expenses.domain.assistant.PendingAction
import com.evgurov.expenses.domain.assistant.Role
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgentTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val ozon = Loan(
        id = 1, type = LoanType.CONSUMER, name = "Кредит Ozon Банк", bank = "Ozon Банк", principal = 200_000_00,
        annualRate = 40.7, termMonths = 60, issueDate = LocalDate.of(2026, 8, 2), monthlyPayment = 7_844_00,
        currentBalance = 197_884_71,
    )
    private val vtb = Loan(
        id = 2, type = LoanType.CONSUMER, name = "Кредит ВТБ", principal = 300_000_00, annualRate = 20.0,
        termMonths = 36, issueDate = LocalDate.of(2026, 3, 18),
    )

    @Test
    fun `parses tool calls in qwen format`() {
        val call = AgentPrompt.parseToolCall(
            "<tool_call>\n{\"name\": \"payoff_plan\", \"arguments\": {\"extra_monthly\": 15000}}\n</tool_call>",
        )!!
        assertEquals("payoff_plan", call.name)
        assertEquals("15000", call.arguments["extra_monthly"].toString())
        val stringArgs = AgentPrompt.parseToolCall("<tool_call>{\"name\":\"save_paydays\",\"arguments\":\"{\\\"days\\\":[5,20]}\"}</tool_call>")!!
        assertEquals("[5,20]", stringArgs.arguments["days"].toString())
        assertNull(AgentPrompt.parseToolCall("Начните с кредита Ozon."))
    }

    @Test
    fun `renders chatml with tools and tool responses`() {
        val tools = LoanTools(listOf(ozon, vtb), 150_000_00, emptyList(), today)
        val system = AgentPrompt.system(tools.facts())
        val prompt = AgentPrompt.render(
            system,
            listOf(AgentMessage(Role.USER, "график"), AgentMessage(Role.ASSISTANT, "<tool_call>{}</tool_call>"), AgentMessage(Role.TOOL, "ok")),
        )
        assertTrue(prompt.startsWith("<|im_start|>system\nТы — помощник"))
        assertTrue("\"name\":\"payment_schedule\"" in prompt)
        assertTrue("Дни зарплаты неизвестны." in prompt)
        assertTrue("<|im_start|>user\n<tool_response>\nok\n</tool_response><|im_end|>\n" in prompt)
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `tools compute plans, schedules and save paydays`() {
        val tools = LoanTools(listOf(ozon, vtb), 150_000_00, emptyList(), today)
        val plan = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"payoff_plan\",\"arguments\":{\"extra_monthly\":10000}}</tool_call>")!!)
        assertTrue(plan.text, "доплата ${Money.format(10_000_00)} в месяц" in plan.text)
        assertTrue("«Кредит Ozon Банк»:" in plan.text)
        val saved = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"save_paydays\",\"arguments\":{\"days\":[20,5]}}</tool_call>")!!)
        assertEquals(listOf(5, 20), saved.paydays)
        val schedule = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"payment_schedule\",\"arguments\":{\"extra_monthly\":\"10 000\"}}</tool_call>")!!)
        assertTrue(schedule.showToUser)
        assertTrue(schedule.text, schedule.text.startsWith("График по зарплатам 5 и 20 числа, с доплатой"))
        val unknown = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"rm\",\"arguments\":{}}</tool_call>")!!)
        assertTrue(unknown.text.startsWith("Нет инструмента"))
    }

    @Test
    fun `finds extra payment needed to close by a target month`() {
        val tools = LoanTools(listOf(ozon, vtb), 150_000_00, emptyList(), today)
        val all = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"extra_for_target\",\"arguments\":{\"months\":13}}</tool_call>")!!)
        assertTrue(all.text, all.text.startsWith("Чтобы закрыть все кредиты за 13 мес."))
        val one = tools.execute(AgentPrompt.parseToolCall("<tool_call>{\"name\":\"extra_for_target\",\"arguments\":{\"months\":13,\"loan\":\"озон\"}}</tool_call>")!!)
        assertTrue(one.text, one.text.startsWith("Чтобы закрыть «Кредит Ozon Банк» за 13 мес."))
        val amount = Regex("""около ([\d\u00A0 ]+)""").find(one.text)!!.groupValues[1].filter(Char::isDigit).toLong()
        // 197 884 ₽ за 13 месяцев при платеже 7 844 ₽: доплата порядка 9–12 тысяч.
        assertTrue(one.text, amount in 8_000..13_000)
        println(all.text)
        println(one.text)
    }
    @Test
    fun `prepares data changes for confirmation and answers about one loan`() {
        val tools = LoanTools(listOf(ozon, vtb), 150_000_00, emptyList(), today)
        fun run(json: String) = tools.execute(AgentPrompt.parseToolCall("<tool_call>$json</tool_call>")!!)

        val early = run("{\"name\":\"record_early_payment\",\"arguments\":{\"loan\":\"озон\",\"amount\":50000}}")
        val save = early.action as PendingAction.SaveLoan
        assertTrue(early.text, early.text.startsWith("Подготовлено и ждёт подтверждения"))
        assertTrue(save.summary, "Остаток станет ${Money.format(147_884_71)}" in save.summary)
        assertEquals(1L, save.loan.id)
        assertTrue(save.loan.bankSchedule.any { it.early && it.payment == 50_000_00L })

        val income = run("{\"name\":\"set_income\",\"arguments\":{\"monthly_income\":200000}}")
        assertEquals(200_000_00L, (income.action as PendingAction.SetIncome).amount)

        val details = run("{\"name\":\"loan_details\",\"arguments\":{\"loan\":\"ВТБ\"}}")
        assertNull(details.action)
        assertTrue(details.text, details.text.startsWith("«Кредит ВТБ»"))
        assertTrue(details.text, "Ближайшие платежи: 18.11.2026" in details.text)

        val forecast = run("{\"name\":\"burden_forecast\",\"arguments\":{}}")
        assertTrue(forecast.text, forecast.text.startsWith("ПДН сейчас"))
        println(save.summary)
        println(details.text)
        println(forecast.text)
    }
}
