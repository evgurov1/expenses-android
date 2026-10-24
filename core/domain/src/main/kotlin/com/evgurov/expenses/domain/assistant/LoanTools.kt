package com.evgurov.expenses.domain.assistant

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.usecase.AdvisorPrompt
import com.evgurov.expenses.domain.usecase.EarlyMode
import com.evgurov.expenses.domain.usecase.EarlyRepayment
import com.evgurov.expenses.domain.usecase.LoanMath
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
data class ToolResult(
    val text: String,
    val showToUser: Boolean = false,
    val paydays: List<Int>? = null,
    /** Изменение данных, которое приложение применит только после подтверждения пользователем. */
    val action: PendingAction? = null,
)

sealed interface PendingAction {
    /** Что именно изменится — показываем пользователю перед кнопкой «Подтвердить». */
    val summary: String

    data class SaveLoan(val loan: Loan, override val summary: String) : PendingAction

    data class SetIncome(val amount: Kopecks, override val summary: String) : PendingAction
}

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
        // Дата меняется каждый день, поэтому она в самом конце: всё до неё модель берёт из кэша.
        val (dateLines, rest) = AdvisorPrompt.facts(loans, advice, 0, income, today).lines()
            // Подсказка для экрана «Как закрыть быстрее» модели не нужна: она начинает просить сумму доплаты.
            .filterNot { it.startsWith("- Укажите, сколько готовы доплачивать") }
            .partition { it.startsWith("Сегодня:") }
        appendLine(rest.joinToString("\n").trim())
        appendLine(
            if (paydays.isEmpty()) "Дни зарплаты неизвестны."
            else "Зарплата приходит ${paydays.joinToString(" и ")} числа.",
        )
        append(dateLines.joinToString("\n"))
    }

    fun execute(call: ToolCall): ToolResult = runCatching {
        when (call.name) {
            "payoff_plan" -> payoffPlan(
                rubles(call.arguments, "extra_monthly"),
                rubles(call.arguments, "lump_sum"),
                strategy(call.arguments),
            )
            "compare_strategies" -> compare(rubles(call.arguments, "extra_monthly"))
            "extra_for_target" -> extraForTarget(
                call.arguments["months"]?.jsonPrimitive?.intOrNull ?: return ToolResult("Нужно число месяцев."),
                call.arguments["loan"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            )
            "payment_schedule" -> schedule(
                call.arguments["extra_monthly"]?.let { rubles(call.arguments, "extra_monthly") } ?: DEFAULT_EXTRA,
                call.arguments["months"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 12) ?: 3,
            )
            "save_paydays" -> savePaydays(call.arguments)
            "record_early_payment" -> recordEarly(call.arguments)
            "set_income" -> setIncome(rubles(call.arguments, "monthly_income"))
            "loan_details" -> loanDetails(call.arguments["loan"]?.jsonPrimitive?.contentOrNull.orEmpty())
            "burden_forecast" -> burdenForecast(rubles(call.arguments, "extra_monthly"))
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

    private fun extraForTarget(months: Int, loanQuery: String?): ToolResult {
        if (months !in 1..600) return ToolResult("Число месяцев должно быть от 1 до 600.")
        val target = YearMonth.from(today).plusMonths(months.toLong())
        val scope = if (loanQuery == null) loans else {
            val found = findLoans(loanQuery)
            when {
                found.isEmpty() -> return ToolResult("Кредит «$loanQuery» не найден. Есть: " + loans.joinToString(", ") { label(it) } + ".")
                found.size > 1 -> return ToolResult("Под «$loanQuery» подходят несколько кредитов: " + found.joinToString(", ") { label(it) } + ". Уточните какой.")
                else -> found
            }
        }
        val what = if (loanQuery == null) "все кредиты" else label(scope.single())
        // Один кредит считаем отдельно: вся доплата идёт в него.
        fun closes(extra: Kopecks): YearMonth? =
            PayoffPlanner.simulate(scope, extra, 0, PayoffStrategy.AVALANCHE, rollover = extra > 0, today).debtFreeMonth
        val baseline = closes(0)
        if (baseline != null && !baseline.isAfter(target)) {
            return ToolResult("Чтобы закрыть $what к ${byMonth(target)}, доплачивать не нужно: по графику это случится ${monthText(baseline)}.")
        }
        var low = 0L
        var high = 100_000_000_00L
        if (closes(high)?.isAfter(target) != false) return ToolResult("За $months мес. закрыть $what не получится даже с очень большой доплатой.")
        while (high - low > 100_00) {
            val mid = (low + high) / 2
            if (closes(mid)?.isAfter(target) == false) high = mid else low = mid
        }
        val extra = (high + 999_00) / 1000_00 * 1000_00
        val advice = PayoffPlanner.advise(loans, extra, income = income, today = today)
        return ToolResult(
            "Чтобы закрыть $what за $months мес. (к ${byMonth(target)}), нужно доплачивать около ${Money.format(extra)} в месяц " +
                "сверх обязательных платежей" + (if (loanQuery != null) " и направлять всю доплату в этот кредит" else "") + ".\n" +
                "Без доплат: ${baseline?.let(::monthText) ?: "нескоро"}.\n" + describe(advice, extra, 0, PayoffStrategy.AVALANCHE),
        )
    }

    private fun findLoans(query: String): List<Loan> {
        fun norm(text: String) = translit(text.lowercase()).replace(Regex("""кредит|банк|bank|[«»"()\s.,-]"""), "")
        val q = norm(query)
        val exact = loans.filter { norm(label(it)) == q }
        if (exact.isNotEmpty()) return exact
        return loans.filter { loan ->
            val keys = listOf(norm(loan.name), norm(loan.bank)).filter { it.isNotBlank() }
            keys.any { it.contains(q) || (q.isNotBlank() && q.contains(it)) }
        }
    }

    private fun translit(text: String): String = text
        .replace("озон", "ozon").replace("т-", "т").replace("тинькофф", "тбанк").replace("tbank", "тбанк").replace("vtb", "втб")

    private fun oneLoan(query: String): Pair<Loan?, String?> {
        val found = findLoans(query)
        return when {
            found.size == 1 -> found.single() to null
            found.isEmpty() -> null to "Кредит «$query» не найден. Есть: " + loans.joinToString(", ") { label(it) } + "."
            else -> null to "Под «$query» подходят несколько кредитов: " + found.joinToString(", ") { label(it) } + ". Уточните какой."
        }
    }

    private fun recordEarly(args: JsonObject): ToolResult {
        val (loan, error) = oneLoan(args["loan"]?.jsonPrimitive?.contentOrNull.orEmpty())
        if (loan == null) return ToolResult(error!!)
        val amount = rubles(args, "amount")
        if (amount <= 0) return ToolResult("Нужна сумма досрочного платежа.")
        val date = args["date"]?.jsonPrimitive?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val mode = if (args["mode"]?.jsonPrimitive?.contentOrNull == "reduce_payment") EarlyMode.REDUCE_PAYMENT else EarlyMode.REDUCE_TERM
        val result = runCatching { EarlyRepayment.apply(loan, date, amount, mode) }.getOrElse { return ToolResult(it.message ?: "Не получилось") }
        val summary = buildString {
            append("Отметить досрочный платёж ${Money.format(amount)} по ${label(loan)} от ${day(date)}")
            append(if (mode == EarlyMode.REDUCE_TERM) ", уменьшить срок" else ", уменьшить платёж")
            append(". Остаток станет ${Money.format(result.balanceAfter)}, платёж ${Money.format(result.newPayment)}")
            result.newEndDate?.let { append(", последний платёж ${monthText(YearMonth.from(it))}") }
            append(", процентов меньше примерно на ${Money.format(result.savedInterest)}.")
        }
        return ToolResult(
            "Подготовлено и ждёт подтверждения пользователя: $summary",
            action = PendingAction.SaveLoan(result.loan, summary),
        )
    }

    private fun setIncome(amount: Kopecks): ToolResult {
        if (amount <= 0) return ToolResult("Нужна сумма дохода в месяц.")
        val payments = loans.sumOf { LoanMath.paymentInMonth(it, YearMonth.from(today)) }
        val summary = "Указать доход ${Money.format(amount)} в месяц. ПДН станет ${"%.0f".format(payments * 100.0 / amount)}%."
        return ToolResult("Подготовлено и ждёт подтверждения пользователя: $summary", action = PendingAction.SetIncome(amount, summary))
    }

    private fun loanDetails(query: String): ToolResult {
        val (loan, error) = oneLoan(query)
        if (loan == null) return ToolResult(error!!)
        val schedule = LoanMath.schedule(loan)
        val next = schedule.filter { !it.early && it.dueDate.isAfter(today) }
        return ToolResult(
            buildString {
                appendLine("${label(loan)}${if (loan.bank.isNotBlank()) ", ${loan.bank}" else ""}.")
                appendLine("Остаток долга ${Money.format(LoanMath.balanceOn(loan, today))}, ставка ${"%.1f".format(loan.annualRate)}%, платёж ${Money.format(LoanMath.regularPayment(loan))}.")
                if (!loan.isCard) {
                    appendLine("Осталось платежей: ${next.size}, процентов впереди ${Money.format(next.sumOf { it.interestPart })}.")
                    next.lastOrNull()?.let { appendLine("Последний платёж ${day(it.dueDate)}.") }
                    appendLine("Ближайшие платежи: " + next.take(3).joinToString("; ") { "${day(it.dueDate)} — ${Money.format(it.payment)} (долг ${Money.format(it.principalPart)}, проценты ${Money.format(it.interestPart)})" } + ".")
                    val early = schedule.filter { it.early }
                    if (early.isNotEmpty()) appendLine("Досрочные платежи: " + early.joinToString("; ") { "${day(it.dueDate)} — ${Money.format(it.payment)}" } + ".")
                } else {
                    loan.creditLimit?.let { appendLine("Лимит ${Money.format(it)}, в ПДН идёт 5% лимита.") }
                }
            }.trim(),
        )
    }

    private fun burdenForecast(extra: Kopecks): ToolResult {
        if (income <= 0) return ToolResult("Доход не указан — ПДН посчитать нельзя. Спросите доход и сохраните его через set_income.")
        val advice = PayoffPlanner.advise(loans, extra, income = income, today = today)
        val plan = if (extra > 0) advice.plan else advice.baseline
        fun pdn(amount: Kopecks) = amount * 100.0 / income
        val now = loans.sumOf { LoanMath.paymentInMonth(it, YearMonth.from(today)) }
        return ToolResult(
            buildString {
                appendLine("ПДН сейчас ${"%.0f".format(pdn(now))}% (платежи ${Money.format(now)} при доходе ${Money.format(income)}).")
                if (extra > 0) appendLine("С доплатой ${Money.format(extra)} в месяц и переносом освободившихся платежей:") else appendLine("Без доплат:")
                for (limit in listOf(80, 50, 30)) {
                    if (pdn(now) < limit) continue
                    val month = plan.mandatoryByMonth.firstOrNull { pdn(it.second) < limit }?.first
                    appendLine("ниже $limit% — ${month?.let(::monthText) ?: "не в обозримом будущем"}")
                }
                if (pdn(now) < 30) appendLine("ПДН уже ниже 30% — это нормальный уровень.")
                appendLine("Зоны: до 30% — нормально, 30–50% — повышенная, 50–80% — высокая (банки отказывают чаще), выше 80% — критическая.")
            }.trim(),
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

    private val monthsDative = listOf(
        "январю", "февралю", "марту", "апрелю", "маю", "июню",
        "июлю", "августу", "сентябрю", "октябрю", "ноябрю", "декабрю",
    )

    private fun byMonth(month: YearMonth) = "${monthsDative[month.monthValue - 1]} ${month.year}"

    private fun day(date: LocalDate) = "%02d.%02d.%d".format(date.dayOfMonth, date.monthValue, date.year)

    private fun monthText(month: YearMonth) = "в ${monthsPrepositional[month.monthValue - 1]} ${month.year}"

    companion object {
        /** Доплата по умолчанию для графика, если пользователь её не назвал. */
        const val DEFAULT_EXTRA: Kopecks = 0
    }
}
