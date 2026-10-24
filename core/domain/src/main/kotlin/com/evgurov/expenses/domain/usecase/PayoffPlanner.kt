package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** Куда направлять лишние деньги. */
enum class PayoffStrategy {
    /** «Лавина»: сначала кредит с самой высокой ставкой — меньше всего переплата. */
    AVALANCHE,

    /** «Снежный ком»: сначала самый маленький остаток — быстрее закрываются кредиты и падает ПДН. */
    SNOWBALL,
}

data class LoanPayoff(
    val loan: Loan,
    val closeMonth: YearMonth?,
    val interest: Kopecks,
)

/** Платёж по кредиту в месяце плана: обязательный по графику или досрочный. */
data class PlannedPayment(
    val month: YearMonth,
    val loan: Loan,
    val amount: Kopecks,
    val early: Boolean,
)

data class PayoffPlan(
    val loans: List<LoanPayoff>,
    val totalInterest: Kopecks,
    /** Месяц, когда закроется последний кредит; null — если за 50 лет не закрывается. */
    val debtFreeMonth: YearMonth?,
    /** Обязательные платежи по месяцам — для прогноза ПДН. */
    val mandatoryByMonth: List<Pair<YearMonth, Kopecks>>,
    /** Все платежи плана по месяцам — для графика по дням зарплаты. */
    val payments: List<PlannedPayment> = emptyList(),
)

data class PayoffAdvice(
    val baseline: PayoffPlan,
    val plan: PayoffPlan,
    val savedInterest: Kopecks,
    val monthsEarlier: Int,
    /** Порядок, в котором гасить кредиты досрочно. */
    val order: List<Loan>,
    val tips: List<String>,
)

/**
 * Считает, как быстрее закрыть кредиты: помесячная симуляция всех кредитов с досрочными платежами.
 * Всё считается на телефоне, по данным кредитов из приложения.
 */
object PayoffPlanner {
    /** Ставка, выше которой стоит смотреть на рефинансирование. */
    const val HIGH_RATE = 25.0

    /** С какой ставкой сравниваем при подсказке про рефинансирование. */
    const val REFINANCE_RATE = 20.0

    private const val MAX_MONTHS = 600
    private const val CARD_MIN_SHARE = 0.05
    private const val CARD_MIN_PAYMENT: Kopecks = 500_00

    fun advise(
        loans: List<Loan>,
        extraMonthly: Kopecks,
        lumpSum: Kopecks = 0,
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
        income: Kopecks = 0,
        today: LocalDate = LocalDate.now(),
    ): PayoffAdvice {
        val active = loans.filter { LoanMath.balanceOn(it, today) > 0 }
        val baseline = simulate(active, extraMonthly = 0, lumpSum = 0, strategy, rollover = false, today)
        val plan = simulate(active, extraMonthly, lumpSum, strategy, rollover = true, today)
        val order = ordered(active.map { State(it, today) }, strategy).map { it.loan }
        val saved = (baseline.totalInterest - plan.totalInterest).coerceAtLeast(0)
        val earlier = if (baseline.debtFreeMonth != null && plan.debtFreeMonth != null) {
            monthsBetween(plan.debtFreeMonth, baseline.debtFreeMonth).coerceAtLeast(0)
        } else {
            0
        }
        return PayoffAdvice(
            baseline = baseline,
            plan = plan,
            savedInterest = saved,
            monthsEarlier = earlier,
            order = order,
            tips = tips(active, plan, order, extraMonthly, lumpSum, saved, earlier, strategy, income, today),
        )
    }

    fun simulate(
        loans: List<Loan>,
        extraMonthly: Kopecks,
        lumpSum: Kopecks,
        strategy: PayoffStrategy,
        rollover: Boolean,
        today: LocalDate = LocalDate.now(),
    ): PayoffPlan {
        val states = loans.map { State(it, today) }.filter { it.balance > 0 }
        val start = YearMonth.from(today).plusMonths(1)
        val mandatory = mutableListOf<Pair<YearMonth, Kopecks>>()
        val payments = mutableListOf<PlannedPayment>()
        var released = 0L
        var month = start
        var step = 0
        while (states.any { it.balance > 0 } && step < MAX_MONTHS) {
            var due = 0L
            for (s in states.filter { it.balance > 0 }) {
                val interest = s.interestFor(month)
                s.balance += interest
                s.interest += interest
                val required = s.minPayment(month)
                var pay = minOf(required, s.balance)
                // Как в банковском графике: копеечный хвост от округлений уходит в последний платёж.
                if (s.balance - pay in 1 until s.regular / 10) pay = s.balance
                due += pay
                if (pay > 0) payments += PlannedPayment(month, s.loan, pay, early = false)
                s.balance -= pay
                if (s.balance == 0L) s.close(month)
            }
            mandatory += month to due
            var pool = extraMonthly + (if (step == 0) lumpSum else 0) + (if (rollover) released else 0)
            for (s in ordered(states, strategy)) {
                if (pool <= 0) break
                val pay = minOf(pool, s.balance)
                if (pay > 0) payments += PlannedPayment(month, s.loan, pay, early = true)
                s.balance -= pay
                pool -= pay
                if (s.balance == 0L) s.close(month)
            }
            released = states.filter { it.closeMonth != null }.sumOf { it.regular }
            month = month.plusMonths(1)
            step++
        }
        val closed = states.all { it.balance == 0L }
        return PayoffPlan(
            loans = states.map { LoanPayoff(it.loan, it.closeMonth, it.interest) },
            totalInterest = states.sumOf { it.interest },
            debtFreeMonth = if (closed) states.mapNotNull { it.closeMonth }.maxOrNull() else null,
            mandatoryByMonth = mandatory,
            payments = payments,
        )
    }

    private fun ordered(states: List<State>, strategy: PayoffStrategy): List<State> {
        val open = states.filter { it.balance > 0 }
        return when (strategy) {
            PayoffStrategy.AVALANCHE -> open.sortedWith(compareByDescending<State> { it.loan.annualRate }.thenBy { it.balance })
            PayoffStrategy.SNOWBALL -> open.sortedWith(compareBy<State> { it.balance }.thenByDescending { it.loan.annualRate })
        }
    }

    private fun tips(
        loans: List<Loan>,
        plan: PayoffPlan,
        order: List<Loan>,
        extraMonthly: Kopecks,
        lumpSum: Kopecks,
        saved: Kopecks,
        earlier: Int,
        strategy: PayoffStrategy,
        income: Kopecks,
        today: LocalDate,
    ): List<String> = buildList {
        if (loans.isEmpty()) return@buildList
        val label = LoanLabels.of(loans, today)
        val first = order.firstOrNull()
        if (first != null && loans.size > 1) {
            add(
                when (strategy) {
                    PayoffStrategy.AVALANCHE ->
                        "Все лишние деньги направляйте в ${label.getValue(first)}: у него самая высокая ставка " +
                            "(${rate(first.annualRate)}), каждый рубль досрочки там экономит больше всего. " +
                            "По остальным платите только обязательный платёж."
                    PayoffStrategy.SNOWBALL ->
                        "Начните с ${label.getValue(first)}: у него самый маленький остаток, он закроется первым, " +
                            "а его платёж вы добавите к досрочке по следующему кредиту."
                },
            )
        }
        if (extraMonthly > 0 || lumpSum > 0) {
            if (saved > 0) {
                val months = if (earlier > 0) " и закроете кредиты на ${monthsText(earlier)} раньше" else ""
                add("Так вы сэкономите ${Money.format(saved)} на процентах$months.")
            }
        } else {
            add("Укажите, сколько готовы доплачивать в месяц — посчитаю, сколько это сэкономит и на сколько раньше вы закроете кредиты.")
        }
        add(
            "При досрочном платеже выбирайте «уменьшить срок»: переплата меньше. " +
                "«Уменьшить платёж» выбирайте, только если важнее быстро снизить ПДН или освободить деньги в месяц.",
        )
        for (loan in loans.filter { !it.isCard && it.annualRate >= HIGH_RATE }) {
            val gain = refinanceGain(loan, today)
            if (gain > 0) {
                add(
                    "${label.getValue(loan).replaceFirstChar { it.uppercase() }}: ставка ${rate(loan.annualRate)} — высокая. Рефинансирование под ${rate(REFINANCE_RATE)} " +
                        "при том же сроке сократит переплату примерно на ${Money.format(gain)}. " +
                        "Сравните предложения банков и учтите страховки и комиссии.",
                )
            }
        }
        for (card in loans.filter { it.isCard }) {
            add(
                "Кредитная карта ${label.getValue(card)} учитывается в ПДН как 5% лимита (${Money.format(LoanMath.cardPayment(card))} в месяц), " +
                    "даже без долга. Если карта не нужна, закройте её или уменьшите лимит — ПДН сразу снизится.",
            )
        }
        if (income > 0) {
            val now = plan.mandatoryByMonth.firstOrNull()?.second ?: 0
            val burdenNow = now * 100.0 / income
            if (burdenNow >= 50) {
                plan.mandatoryByMonth.firstOrNull { it.second * 100.0 / income < 50 }?.let { (month, _) ->
                    add("По этому плану ПДН опустится ниже 50% к ${monthTitle(month)} — тогда банки охотнее дают рефинансирование.")
                }
            }
        }
    }

    /** Сколько процентов сэкономит перевод остатка под [REFINANCE_RATE] на оставшийся срок. */
    private fun refinanceGain(loan: Loan, today: LocalDate): Kopecks {
        val balance = LoanMath.balanceOn(loan, today)
        val months = LoanMath.endDate(loan)?.let { monthsBetween(YearMonth.from(today), YearMonth.from(it)) } ?: return 0
        if (balance <= 0 || months <= 0) return 0
        fun overpay(rate: Double) = LoanMath.annuityPayment(balance, rate, months) * months - balance
        return (overpay(loan.annualRate) - overpay(REFINANCE_RATE)).coerceAtLeast(0)
    }

    private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
        (to.year - from.year) * 12 + (to.monthValue - from.monthValue)

    private fun rate(value: Double): String = "%.1f%%".format(value).replace('.', ',').replace(",0%", "%")

    private fun monthsText(months: Int): String {
        val years = months / 12
        val rest = months % 12
        fun plural(n: Int, one: String, few: String, many: String) = when {
            n % 100 in 11..14 -> many
            n % 10 == 1 -> one
            n % 10 in 2..4 -> few
            else -> many
        }
        return listOfNotNull(
            years.takeIf { it > 0 }?.let { "$it ${plural(it, "год", "года", "лет")}" },
            rest.takeIf { it > 0 }?.let { "$it ${plural(it, "месяц", "месяца", "месяцев")}" },
        ).joinToString(" ")
    }

    private val monthsGenitive = listOf(
        "январю", "февралю", "марту", "апрелю", "маю", "июню",
        "июлю", "августу", "сентябрю", "октябрю", "ноябрю", "декабрю",
    )

    private fun monthTitle(month: YearMonth): String = "${monthsGenitive[month.monthValue - 1]} ${month.year}"

    private class State(val loan: Loan, today: LocalDate) {
        var balance: Kopecks = LoanMath.balanceOn(loan, today)
        var interest: Kopecks = 0
        var closeMonth: YearMonth? = null
        val monthlyRate = loan.annualRate / 100.0 / 12.0
        val regular: Kopecks = LoanMath.regularPayment(loan).takeIf { it > 0 && !loan.isCard }
            ?: fallbackPayment(today)

        /** Обязательный платёж месяца: по графику банка (с его каникулами и «процентными» месяцами) или регулярный. */
        fun minPayment(month: YearMonth): Kopecks = when {
            loan.isCard -> max((balance * CARD_MIN_SHARE).roundToLong(), CARD_MIN_PAYMENT)
            scheduleEnd != null && !month.isAfter(scheduleEnd) ->
                loan.bankSchedule.filter { YearMonth.from(it.dueDate) == month }.sumOf { it.payment }
            else -> regular
        }

        private val scheduleByMonth = loan.bankSchedule.groupBy { YearMonth.from(it.dueDate) }

        /**
         * Проценты за месяц. Пока идёт график банка — берём его проценты (там учтены каникулы и отложенные проценты),
         * пропорционально фактическому остатку после досрочек. Дальше — по ставке.
         */
        fun interestFor(month: YearMonth): Kopecks {
            val items = scheduleByMonth[month]
            if (items != null) {
                val scheduledBefore = items.first().let { it.balanceAfter + it.principalPart }
                val scheduledInterest = items.sumOf { it.interestPart }
                if (scheduledBefore > 0) return (scheduledInterest.toDouble() * balance / scheduledBefore).roundToLong()
            }
            if (scheduleEnd != null && !month.isAfter(scheduleEnd)) return 0
            return (balance * monthlyRate).roundToLong()
        }

        private val scheduleEnd: YearMonth? = loan.bankSchedule.lastOrNull()?.let { YearMonth.from(it.dueDate) }

        fun close(month: YearMonth) {
            if (closeMonth == null) closeMonth = month
        }

        /** Платёж неизвестен: считаем аннуитет на оставшийся срок или на 5 лет. */
        private fun fallbackPayment(today: LocalDate): Kopecks {
            if (loan.isCard) return max((balance * CARD_MIN_SHARE).roundToLong(), CARD_MIN_PAYMENT)
            val left = LoanMath.endDate(loan)?.let { end ->
                (end.year - today.year) * 12 + (end.monthValue - today.monthValue)
            }?.takeIf { it > 0 } ?: 60
            val r = monthlyRate
            if (r == 0.0) return (balance + left - 1) / left
            val k = (1 + r).pow(left)
            return (balance * r * k / (k - 1)).roundToLong()
        }
    }
}
