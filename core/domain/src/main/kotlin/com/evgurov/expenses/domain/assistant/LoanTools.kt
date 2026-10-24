package com.evgurov.expenses.domain.assistant

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.AdvisorPrompt
import com.evgurov.expenses.domain.usecase.LoanLabels
import com.evgurov.expenses.domain.usecase.PaydaySchedule
import com.evgurov.expenses.domain.usecase.PayoffAdvice
import com.evgurov.expenses.domain.usecase.PayoffPlanner
import com.evgurov.expenses.domain.usecase.PayoffStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.YearMonth

/**
 * Результат инструмента.
 * @param showToUser готовый текст для пользователя (график) — показываем как есть, модель его не пересказывает.
 * @param paydays новые дни зарплаты, которые нужно сохранить.
 */
data class ToolResult(val text: String, val showToUser: Boolean = false, val paydays: List<Int>? = null)

/** Инструменты помощника. Все расчёты — в приложении, модель только выбирает инструмент и объясняет итог. */
class LoanTools(
    private val loans: List<Loan>,
    private val income: Kopecks,
    private var paydays: List<Int>,
    private val today: LocalDate,
) {
    private val labels = LoanLabels.of(loans, today)

    /** Данные для системного запроса: кредиты, доход, ПДН, порядок погашения, дни зарплаты. */
    fun facts(): String = buildString {
        val advice = PayoffPlanner.advise(loans, 0, income = income, today = today)
        append(AdvisorPrompt.facts(loans, advice, 0, income, today))
        appendLine()
        append(
            if (paydays.isEmpty()) "Дни зарплаты неизвестны."
            else "Зарплата приходит ${paydays.joinToString(" и ")} числа.",
        )
    }

    fun execute(call: ToolCall): ToolResult = runCatching {
        when (call.name) {
            "payoff_plan" -> payoffPlan(
                rubles(call.arguments, "extra_monthly"),
                rubles(call.arguments, "lump_sum"),
                strategy(call.arguments),
            )
            "compare_strategies" -> compare(rubles(call.arguments, "extra_monthly"))
            "payment_schedule" -> schedule(
                call.arguments["extra_monthly"]?.let { rubles(call.arguments, "extra_monthly") } ?: DEFAULT_EXTRA,
                call.arguments["months"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 12) ?: 3,
            )
            "save_paydays" -> savePaydays(call.arguments)
            else -> ToolResult("Нет инструмента ${call.name}.")
        }
    }.getOrElse { ToolResult("Ошибка расчёта: ${it.message}") }

    private fun payoffPlan(extra: Kopecks, lump: Kopecks, strategy: PayoffStrategy): ToolResult {
        val advice = PayoffPlanner.advise(loans, extra, lump, strategy, income, today)
        return ToolResult(describe(advice, extra, lump, strategy))
    }

    private fun compare(extra: Kopecks): ToolResult {
        val avalanche = PayoffPlanner.advise(loans, extra, 0, PayoffStrategy.AVALANCHE, income, today)
        val snowball = PayoffPlanner.advise(loans, extra, 0, PayoffStrategy.SNOWBALL, income, today)
        return ToolResult(
            describe(avalanche, extra, 0, PayoffStrategy.AVALANCHE) + "\n\n" +
                describe(snowball, extra, 0, PayoffStrategy.SNOWBALL),
        )
    }

    private fun schedule(extra: Kopecks, months: Int): ToolResult {
        val advice = PayoffPlanner.advise(loans, extra, income = income, today = today)
        val schedule = PaydaySchedule.build(advice.plan, paydays, today, months)
        if (schedule.isEmpty()) return ToolResult("Действующих кредитов нет — график пуст.")
        return ToolResult(
            PaydaySchedule.describe(schedule, paydays, loans, extra, advice.order.firstOrNull(), today),
            showToUser = true,
        )
    }

    private fun savePaydays(args: JsonObject): ToolResult {
        val days = (args["days"] as? JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.contentOrNull?.toIntOrNull() }
            .filter { it in 1..31 }.distinct().sorted()
        if (days.isEmpty()) return ToolResult("Не понял дни зарплаты. Нужны числа месяца от 1 до 31.")
        paydays = days
        return ToolResult("Сохранено: зарплата ${days.joinToString(" и ")} числа.", paydays = days)
    }

    private fun describe(advice: PayoffAdvice, extra: Kopecks, lump: Kopecks, strategy: PayoffStrategy): String = buildString {
        append(if (strategy == PayoffStrategy.AVALANCHE) "Стратегия «лавина»" else "Стратегия «снежный ком»")
        append(", доплата ${Money.format(extra)} в месяц")
        if (lump > 0) append(", разово ${Money.format(lump)} сейчас")
        appendLine(".")
        appendLine(
            "Без доплат: все кредиты закроются ${advice.baseline.debtFreeMonth?.let(::monthText) ?: "нескоро"}, " +
                "проценты ${Money.format(advice.baseline.totalInterest)}.",
        )
        appendLine(
            "С доплатой: все кредиты закроются ${advice.plan.debtFreeMonth?.let(::monthText) ?: "нескоро"}" +
                (if (advice.monthsEarlier > 0) " (на ${advice.monthsEarlier} мес. раньше)" else "") +
                ", проценты ${Money.format(advice.plan.totalInterest)}, экономия ${Money.format(advice.savedInterest)}.",
        )
        appendLine("Порядок досрочки: " + advice.order.joinToString(", ") { label(it) } + ".")
        appendLine("Когда закроется каждый кредит (без доплат → с доплатой):")
        for (planned in advice.plan.loans) {
            val base = advice.baseline.loans.firstOrNull { it.loan == planned.loan }?.closeMonth
            append("- ${label(planned.loan)}: ${base?.let(::monthText) ?: "—"} → ${planned.closeMonth?.let(::monthText) ?: "—"}")
            appendLine()
        }
    }.trim()

    private fun label(loan: Loan) = labels[loan] ?: "«${loan.name}»"

    private fun rubles(args: JsonObject, key: String): Kopecks {
        val primitive = args[key]?.jsonPrimitive ?: return 0
        val value = primitive.doubleOrNull ?: primitive.contentOrNull?.let { Money.parse(it)?.let { k -> k / 100.0 } } ?: 0.0
        return (value * 100).toLong().coerceIn(0, 100_000_000_00)
    }

    private fun strategy(args: JsonObject): PayoffStrategy =
        if (args["strategy"]?.jsonPrimitive?.contentOrNull == "snowball") PayoffStrategy.SNOWBALL else PayoffStrategy.AVALANCHE

    private val monthsPrepositional = listOf(
        "январе", "феврале", "марте", "апреле", "мае", "июне",
        "июле", "августе", "сентябре", "октябре", "ноябре", "декабре",
    )

    private fun monthText(month: YearMonth) = "в ${monthsPrepositional[month.monthValue - 1]} ${month.year}"

    companion object {
        /** Доплата по умолчанию для графика, если пользователь её не назвал. */
        const val DEFAULT_EXTRA: Kopecks = 0
    }
}
