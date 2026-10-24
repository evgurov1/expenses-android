package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

data class PaydayItem(
    val loan: Loan,
    val amount: Kopecks,
    /** Дата списания по графику банка; для досрочного платежа — null. */
    val dueDate: LocalDate?,
)

data class Payday(
    val date: LocalDate,
    /** Зарплата уже пришла: платёж до следующей зарплаты, деньги нужно отложить сейчас. */
    val alreadyPaid: Boolean,
    val items: List<PaydayItem>,
) {
    val total: Kopecks get() = items.sumOf { it.amount }
}

/**
 * Раскладывает платежи плана по дням зарплаты: обязательный платёж — из последней зарплаты до даты списания,
 * досрочку месяца — поровну на зарплаты этого месяца.
 */
object PaydaySchedule {
    private val daysInQuestion = Regex("""(?<!\d)(\d{1,2})(?!\d)""")

    /** Дни зарплаты из фразы вроде «зарплата 5 и 20 числа». Пусто — если дней в вопросе нет. */
    fun paydaysFromText(text: String): List<Int> {
        val lower = text.lowercase()
        if (!lower.contains("числ")) return emptyList()
        val head = lower.substringBeforeLast("числ")
        return daysInQuestion.findAll(head).mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..31 }.distinct().sorted().toList()
    }

    fun build(plan: PayoffPlan, paydays: List<Int>, today: LocalDate, months: Int = 3): List<Payday> {
        val days = paydays.filter { it in 1..31 }.distinct().sorted()
        if (days.isEmpty()) return emptyList()
        val first = plan.payments.firstOrNull()?.month ?: return emptyList()
        // Берём на месяц больше: платёж в начале следующего месяца оплачивается с зарплаты последнего показанного.
        val shown = (0..months).map { first.plusMonths(it.toLong()) }.toSet()
        val end = first.plusMonths(months.toLong()).atDay(1)
        val byDate = sortedMapOf<LocalDate, MutableList<PaydayItem>>()
        for (payment in plan.payments.filter { it.month in shown }) {
            if (payment.early) {
                val dates = days.map { dayIn(payment.month, it) }
                val parts = split(payment.amount, dates.size)
                dates.zip(parts).filter { it.second > 0 }.forEach { (date, part) ->
                    byDate.getOrPut(date.coerceAtLeast(today)) { mutableListOf() } += PaydayItem(payment.loan, part, null)
                }
            } else {
                val due = dueDate(payment.loan, payment.month)
                val payday = latestPayday(days, due)
                byDate.getOrPut(payday.coerceAtLeast(today)) { mutableListOf() } += PaydayItem(payment.loan, payment.amount, due)
            }
        }
        return byDate.filterKeys { it.isBefore(end) }.map { (date, items) ->
            Payday(
                date = date,
                alreadyPaid = date == today && days.none { dayIn(YearMonth.from(today), it) == today },
                items = items.groupBy { it.loan to it.dueDate }
                    .map { (key, list) -> PaydayItem(key.first, list.sumOf { it.amount }, key.second) }
                    .sortedWith(compareBy<PaydayItem> { it.dueDate == null }.thenBy { it.dueDate }),
            )
        }
    }

    /** Готовый ответ для чата: по каждой зарплате — что и до какого числа оплатить. */
    fun describe(
        schedule: List<Payday>,
        paydays: List<Int>,
        loans: List<Loan>,
        extraMonthly: Kopecks,
        firstTarget: Loan?,
        today: LocalDate,
    ): String = buildString {
        val label = LoanLabels.of(loans, today)
        append("График по зарплатам ${paydays.joinToString(" и ")} числа")
        if (extraMonthly > 0) {
            append(", с доплатой ${Money.format(extraMonthly)} в месяц")
            firstTarget?.let { append(" (досрочка сначала в ${label[it] ?: "«${it.name}»"})") }
        }
        appendLine(":")
        for (payday in schedule) {
            appendLine()
            appendLine(
                if (payday.alreadyPaid) "Из денег, которые уже есть (до следующей зарплаты) — ${Money.format(payday.total)}:"
                else "${dateText(payday.date)}, с зарплаты — ${Money.format(payday.total)}:",
            )
            for (item in payday.items) {
                val name = label[item.loan] ?: "«${item.loan.name}»"
                if (item.dueDate != null) {
                    appendLine("• $name — ${Money.format(item.amount)}, спишут ${dateText(item.dueDate)}")
                } else {
                    appendLine("• $name — досрочно ${Money.format(item.amount)}")
                }
            }
        }
        appendLine()
        append(
            "Платёж кладите на счёт кредита сразу в день зарплаты. Досрочку оформляйте в приложении банка " +
                "с уменьшением срока. Доплату можно поменять на экране «Как закрыть быстрее».",
        )
    }.trim()

    private fun split(amount: Kopecks, parts: Int): List<Kopecks> {
        if (parts <= 1) return listOf(amount)
        // Делим до рублей, остаток — в последнюю часть.
        val share = amount / parts / 100 * 100
        return List(parts - 1) { share } + (amount - share * (parts - 1))
    }

    private fun dayIn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

    private fun latestPayday(days: List<Int>, due: LocalDate): LocalDate {
        var month = YearMonth.from(due)
        repeat(3) {
            days.map { dayIn(month, it) }.filter { !it.isAfter(due) }.maxOrNull()?.let { return it }
            month = month.minusMonths(1)
        }
        return due
    }

    /** Дата списания в месяце: из графика банка или по дню выдачи кредита. */
    private fun dueDate(loan: Loan, month: YearMonth): LocalDate =
        LoanMath.schedule(loan).firstOrNull { YearMonth.from(it.dueDate) == month }?.dueDate
            ?: dayIn(month, loan.issueDate.dayOfMonth)

    private val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    private fun dateText(date: LocalDate): String = "${date.dayOfMonth} ${monthsGenitive[date.monthValue - 1]}"
}
