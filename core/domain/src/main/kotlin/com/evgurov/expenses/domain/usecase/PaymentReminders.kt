package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * @param paidKeys метки платежей из напоминания: по кнопке «Уже оплатил» они запоминаются,
 * и о них приложение больше не напоминает.
 */
data class Reminder(val id: Int, val title: String, val text: String, val paidKeys: List<String> = emptyList())

/** Платёж по кредиту, который уже прошёл: операция в категории «Платежи по кредитам». */
data class MadePayment(val date: LocalDate, val amount: Kopecks)

/** Напоминания на день: в день зарплаты — сколько отложить, за 2 дня до списания — какой платёж. */
object PaymentReminders {
    const val DAYS_BEFORE = 2L

    private data class Due(val loan: Loan, val date: LocalDate, val amount: Kopecks) {
        val key: String get() = key(loan.id, date)
    }

    fun key(loanId: Long, date: LocalDate) = "$loanId:$date"

    /**
     * @param paidKeys платежи, которые пользователь отметил оплаченными.
     * @param made уже прошедшие платежи по кредитам из операций.
     */
    fun forDay(
        loans: List<Loan>,
        paydays: List<Int>,
        today: LocalDate,
        paidKeys: Set<String> = emptySet(),
        made: List<MadePayment> = emptyList(),
    ): List<Reminder> {
        val labels = LoanLabels.of(loans, today)
        val dues = notPaid(upcoming(loans, today), today, paidKeys, made)
        val result = mutableListOf<Reminder>()

        val days = paydays.filter { it in 1..31 }.distinct().sorted()
        val isPayday = days.any { YearMonth.from(today).atDay(minOf(it, YearMonth.from(today).lengthOfMonth())) == today }
        if (isPayday) {
            val fromThisPay = dues.filter { PaydaySchedule.latestPayday(days, it.date) == today }
            if (fromThisPay.isNotEmpty()) {
                result += Reminder(
                    id = 1,
                    title = "Зарплата: отложите ${Money.format(fromThisPay.sumOf { it.amount })} на кредиты",
                    text = fromThisPay.joinToString("\n") {
                        "${labels[it.loan] ?: it.loan.name} — ${Money.format(it.amount)} до ${dateText(it.date)}"
                    },
                    paidKeys = fromThisPay.map { it.key },
                )
            }
        }

        val soon = dues.filter { it.date == today.plusDays(DAYS_BEFORE) }
        if (soon.isNotEmpty()) {
            result += Reminder(
                id = 2,
                title = "Через 2 дня списание ${Money.format(soon.sumOf { it.amount })}",
                text = soon.joinToString("\n") { "${labels[it.loan] ?: it.loan.name} — ${Money.format(it.amount)}, ${dateText(it.date)}" } +
                    "\nПроверьте, что деньги на счёте кредита.",
                paidKeys = soon.map { it.key },
            )
        }
        return result
    }

    /**
     * Убирает платежи, которые уже внесены: отмечены пользователем, покрыты досрочным платежом
     * или операцией по кредиту за месяц до даты списания. Одна операция закрывает один платёж.
     */
    private fun notPaid(dues: List<Due>, today: LocalDate, paidKeys: Set<String>, made: List<MadePayment>): List<Due> {
        val unused = made.sortedBy { it.date }.toMutableList()
        return dues.filter { due ->
            if (due.key in paidKeys) return@filter false
            val from = due.date.minusMonths(1)
            val early = LoanMath.schedule(due.loan)
                .filter { it.early && it.dueDate.isAfter(from) && !it.dueDate.isAfter(today) }
                .sumOf { it.payment }
            if (early >= due.amount - TOLERANCE) return@filter false
            val payment = unused.firstOrNull { it.date.isAfter(from) && !it.date.isAfter(today) && it.amount >= due.amount - TOLERANCE }
            if (payment != null) {
                unused.remove(payment)
                return@filter false
            }
            true
        }
    }

    /** Банк может округлить платёж: разница до рубля не мешает считать его внесённым. */
    private const val TOLERANCE = 100L

    /** Обязательные платежи на ближайшие полтора месяца. */
    private fun upcoming(loans: List<Loan>, today: LocalDate): List<Due> =
        loans.filter { !it.isCard && LoanMath.balanceOn(it, today) > 0 }.flatMap { loan ->
            val regular = LoanMath.regularPayment(loan)
            LoanMath.schedule(loan)
                .filter { !it.early && it.dueDate.isAfter(today) && !it.dueDate.isAfter(today.plusDays(45)) }
                .map { item ->
                    val last = item.balanceAfter == 0L
                    Due(loan, item.dueDate, if (loan.monthlyPayment != null && !last) regular else item.payment)
                }
        }.sortedBy { it.date }

    private val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    private fun dateText(date: LocalDate) = "${date.dayOfMonth} ${monthsGenitive[date.monthValue - 1]}"
}
