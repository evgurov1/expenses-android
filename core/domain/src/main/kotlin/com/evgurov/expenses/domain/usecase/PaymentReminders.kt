package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

data class Reminder(val id: Int, val title: String, val text: String)

/** Напоминания на день: в день зарплаты — сколько отложить, за 2 дня до списания — какой платёж. */
object PaymentReminders {
    const val DAYS_BEFORE = 2L

    private data class Due(val loan: Loan, val date: LocalDate, val amount: Kopecks)

    fun forDay(loans: List<Loan>, paydays: List<Int>, today: LocalDate): List<Reminder> {
        val labels = LoanLabels.of(loans, today)
        val dues = upcoming(loans, today)
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
            )
        }
        return result
    }

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
