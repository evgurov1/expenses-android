package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

data class ChatTurn(val question: String, val answer: String)

/**
 * Готовит запрос к локальной модели. Модель только пересказывает и объясняет:
 * все суммы и даты берутся из расчётов [PayoffPlanner], а не придумываются ею.
 */
object AdvisorPrompt {
    private val amountInQuestion = Regex("""(\d[\d  ]*(?:[.,]\d{1,2})?)\s*(?:тыс\p{L}*|к(?!\p{L}))?""")

    private const val RULES =
        "Ты — помощник по кредитам в мобильном приложении. Отвечай по-русски, коротко: 2–5 предложений, без таблиц. " +
            "Используй только цифры и даты из раздела «Факты». Не придумывай суммы, ставки и даты и ничего не пересчитывай сам. " +
            "Если в фактах нет ответа, так и скажи и предложи изменить доплату на экране «Как закрыть быстрее». " +
            "Не советуй брать новые кредиты и займы, кроме рефинансирования под меньшую ставку. " +
            "Ты не финансовый консультант: для решений о рефинансировании советуй сравнить предложения банков."

    /** Сумма доплаты из вопроса: «если вносить 15 000 в месяц», «15 тыс», «20к». Null — если суммы нет. */
    fun extraFromQuestion(question: String): Kopecks? {
        val lower = question.lowercase()
        if (!lower.contains("мес") && !lower.contains("доплач") && !lower.contains("вносить") && !lower.contains("плат")) return null
        val match = amountInQuestion.findAll(lower).firstOrNull { it.groupValues[1].any(Char::isDigit) } ?: return null
        val base = Money.parse(match.groupValues[1].replace(" ", "").replace(" ", "")) ?: return null
        val thousands = match.value.contains("тыс") || match.value.trimEnd().endsWith("к")
        val amount = if (thousands) base * 1000 else base
        return amount.takeIf { it in 100_00..100_000_000_00 }
    }

    fun facts(
        loans: List<Loan>,
        advice: PayoffAdvice,
        extraMonthly: Kopecks,
        income: Kopecks,
        today: LocalDate,
        whatIf: Pair<Kopecks, PayoffAdvice>? = null,
    ): String = buildString {
        appendLine("Сегодня: ${today}.")
        if (income > 0) appendLine("Доход в месяц: ${Money.format(income)}.")
        val month = YearMonth.from(today)
        val payments = loans.sumOf { LoanMath.paymentInMonth(it, month) }
        if (income > 0) {
            appendLine("Обязательные платежи в этом месяце: ${Money.format(payments)}, ПДН ${"%.0f".format(payments * 100.0 / income)}%.")
        }
        appendLine("Кредиты:")
        val active = loans.filter { LoanMath.balanceOn(it, today) > 0 }
        for (loan in active) {
            val close = advice.baseline.loans.firstOrNull { it.loan == loan }?.closeMonth
            append("- «${loan.name}»")
            if (loan.bank.isNotBlank()) append(", ${loan.bank}")
            append(": остаток ${Money.format(LoanMath.balanceOn(loan, today))}")
            if (loan.annualRate > 0) append(", ставка ${"%.1f".format(loan.annualRate)}%")
            append(", платёж ${Money.format(LoanMath.regularPayment(loan))} в месяц")
            close?.let { append(", без досрочек закроется ${monthText(it)}") }
            appendLine(".")
        }
        appendLine(
            "Без досрочных платежей: переплата по процентам ${Money.format(advice.baseline.totalInterest)}, " +
                "все кредиты закроются ${advice.baseline.debtFreeMonth?.let(::monthText) ?: "нескоро"}.",
        )
        describePlan(advice, extraMonthly)
        whatIf?.let { (extra, other) -> describePlan(other, extra) }
        if (advice.order.size > 1) {
            appendLine("Порядок досрочного погашения: " + advice.order.joinToString(", ") { "«${it.name}»" } + ".")
        }
        if (advice.tips.isNotEmpty()) {
            appendLine("Советы приложения:")
            advice.tips.forEach { appendLine("- $it") }
        }
    }.trim()

    private fun StringBuilder.describePlan(advice: PayoffAdvice, extra: Kopecks) {
        if (extra <= 0) return
        append("Если доплачивать ${Money.format(extra)} в месяц: ")
        append("экономия на процентах ${Money.format(advice.savedInterest)}, ")
        append("все кредиты закроются ${advice.plan.debtFreeMonth?.let(::monthText) ?: "нескоро"}")
        if (advice.monthsEarlier > 0) append(" (на ${advice.monthsEarlier} мес. раньше)")
        val closes = advice.plan.loans.mapNotNull { p -> p.closeMonth?.let { "«${p.loan.name}» — ${monthText(it)}" } }
        if (closes.isNotEmpty()) append("; закрытие: ${closes.joinToString(", ")}")
        appendLine(".")
    }

    fun build(facts: String, history: List<ChatTurn>, question: String): String = buildString {
        appendLine(RULES)
        appendLine()
        appendLine("Факты:")
        appendLine(facts)
        for (turn in history.takeLast(3)) {
            appendLine()
            appendLine("Вопрос: ${turn.question}")
            appendLine("Ответ: ${turn.answer}")
        }
        appendLine()
        appendLine("Вопрос: $question")
        append("Ответ:")
    }

    private val months = listOf(
        "январе", "феврале", "марте", "апреле", "мае", "июне",
        "июле", "августе", "сентябре", "октябре", "ноябре", "декабре",
    )

    private fun monthText(month: YearMonth): String = "в ${months[month.monthValue - 1]} ${month.year}"
}
