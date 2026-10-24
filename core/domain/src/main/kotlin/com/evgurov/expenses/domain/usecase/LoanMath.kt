package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.model.ScheduleItem
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow
import kotlin.math.roundToLong

object LoanMath {
    /** Доля лимита кредитной карты, которую банки и ЦБ считают ежемесячным платежом. */
    const val CARD_LIMIT_SHARE = 0.05

    private fun monthlyRate(annualRate: Double) = annualRate / 100.0 / 12.0

    /** Аннуитетный платёж: P = S · r(1+r)^n / ((1+r)^n − 1). */
    fun annuityPayment(principal: Kopecks, annualRate: Double, termMonths: Int): Kopecks {
        require(termMonths > 0) { "Срок должен быть больше нуля" }
        if (principal <= 0) return 0
        val r = monthlyRate(annualRate)
        if (r == 0.0) return (principal + termMonths - 1) / termMonths
        val k = (1 + r).pow(termMonths)
        return (principal * r * k / (k - 1)).roundToLong()
    }

    /** Платёж из договора или рассчитанный по условиям. */
    fun regularPayment(loan: Loan): Kopecks = when {
        loan.isCard -> cardPayment(loan)
        loan.monthlyPayment != null -> loan.monthlyPayment
        loan.termMonths <= 0 -> 0
        loan.paymentType == PaymentType.ANNUITY -> annuityPayment(loan.principal, loan.annualRate, loan.termMonths)
        else -> schedule(loan).firstOrNull()?.payment ?: 0
    }

    fun cardPayment(loan: Loan): Kopecks = ((loan.creditLimit ?: 0) * CARD_LIMIT_SHARE).roundToLong()

    /** Полный график платежей с момента выдачи. Для кредитной карты график пустой. */
    fun schedule(loan: Loan): List<ScheduleItem> {
        if (loan.isCard || loan.termMonths <= 0 || loan.principal <= 0) return emptyList()
        val r = monthlyRate(loan.annualRate)
        val annuity = loan.monthlyPayment ?: annuityPayment(loan.principal, loan.annualRate, loan.termMonths)
        val principalShare = loan.principal / loan.termMonths
        var balance = loan.principal
        val items = mutableListOf<ScheduleItem>()
        for (n in 1..loan.termMonths) {
            if (balance <= 0) break
            val interest = (balance * r).roundToLong()
            val last = n == loan.termMonths
            val principalPart = when {
                last -> balance
                loan.paymentType == PaymentType.ANNUITY -> (annuity - interest).coerceIn(0, balance)
                else -> principalShare.coerceAtMost(balance)
            }
            balance -= principalPart
            items += ScheduleItem(
                number = n,
                dueDate = loan.issueDate.plusMonths(n.toLong()),
                payment = principalPart + interest,
                principalPart = principalPart,
                interestPart = interest,
                balanceAfter = balance,
            )
        }
        return items
    }

    /** Остаток долга на дату: значение пользователя, иначе по графику. Для карты — введённый долг. */
    fun balanceOn(loan: Loan, date: LocalDate): Kopecks {
        loan.currentBalance?.let { return it }
        if (loan.isCard) return 0
        val paid = schedule(loan).lastOrNull { !it.dueDate.isAfter(date) }
        return paid?.balanceAfter ?: loan.principal
    }

    /** Дата последнего платежа по графику или null для карты. */
    fun endDate(loan: Loan): LocalDate? = schedule(loan).lastOrNull()?.dueDate

    /**
     * Сколько платить по кредиту в указанном месяце для расчёта ПДН.
     * Карта — 5% лимита, пока лимит есть; кредит — платёж по графику, если он попадает в этот месяц.
     */
    fun paymentInMonth(loan: Loan, month: YearMonth): Kopecks {
        if (loan.isCard) return cardPayment(loan)
        val items = schedule(loan)
        if (items.isEmpty()) return loan.monthlyPayment ?: 0
        val item = items.firstOrNull { YearMonth.from(it.dueDate) == month } ?: return 0
        // Если пользователь указал платёж из договора, в ПДН берём его, а не последний «хвост» графика.
        return if (loan.monthlyPayment != null && item.number < items.size) loan.monthlyPayment else item.payment
    }
}
