package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.ScheduleItem
import java.time.LocalDate
import kotlin.math.roundToLong

enum class EarlyMode {
    /** Платёж прежний, кредит закрывается раньше — переплата меньше. */
    REDUCE_TERM,

    /** Срок прежний, платёж меньше — освобождаются деньги и падает ПДН. */
    REDUCE_PAYMENT,
}

data class EarlyResult(
    val loan: Loan,
    val balanceAfter: Kopecks,
    val newPayment: Kopecks,
    val newEndDate: LocalDate?,
    /** На сколько меньше процентов по новому графику, чем по старому. */
    val savedInterest: Kopecks,
)

/**
 * Досрочный платёж, который пользователь внёс в банке: пересчитываем оставшийся график.
 * Проценты за первый месяц банк считает по дням, поэтому новый график — оценка; точный даст банк.
 */
object EarlyRepayment {
    fun apply(loan: Loan, date: LocalDate, amount: Kopecks, mode: EarlyMode): EarlyResult {
        require(amount > 0) { "Сумма должна быть больше нуля" }
        if (loan.isCard) {
            val balance = ((loan.currentBalance ?: 0) - amount).coerceAtLeast(0)
            val updated = loan.copy(currentBalance = balance)
            return EarlyResult(updated, balance, LoanMath.cardPayment(updated), null, 0)
        }
        val old = LoanMath.schedule(loan)
        val before = loan.currentBalance ?: old.lastOrNull { !it.dueDate.isAfter(date) }?.balanceAfter ?: loan.principal
        require(amount <= before) { "Сумма больше остатка долга" }
        val balance = before - amount

        val past = old.filter { !it.dueDate.isAfter(date) }
        val futureDates = old.filter { it.dueDate.isAfter(date) && !it.early }.map { it.dueDate }
        val oldFutureInterest = old.filter { it.dueDate.isAfter(date) }.sumOf { it.interestPart }
        val oldPayment = LoanMath.regularPayment(loan)
        val rate = loan.annualRate / 100.0 / 12.0

        val payment = when (mode) {
            EarlyMode.REDUCE_TERM -> oldPayment
            EarlyMode.REDUCE_PAYMENT -> LoanMath.annuityPayment(balance, loan.annualRate, futureDates.size.coerceAtLeast(1))
        }
        val future = mutableListOf<ScheduleItem>()
        var left = balance
        var due = futureDates.firstOrNull() ?: date.plusMonths(1)
        var index = 0
        while (left > 0 && future.size < 600) {
            val interest = (left * rate).roundToLong()
            val last = payment - interest >= left || (mode == EarlyMode.REDUCE_PAYMENT && index == futureDates.size - 1)
            val principalPart = if (last) left else (payment - interest).coerceIn(1, left)
            left -= principalPart
            future += ScheduleItem(0, due, principalPart + interest, principalPart, interest, left)
            index++
            due = futureDates.getOrNull(index) ?: due.plusMonths(1)
        }

        val early = ScheduleItem(0, date, amount, amount, 0, balance, early = true)
        val schedule = (past + early + future).mapIndexed { i, item -> item.copy(number = i + 1) }
        val updated = loan.copy(
            bankSchedule = schedule,
            monthlyPayment = if (mode == EarlyMode.REDUCE_PAYMENT || loan.monthlyPayment != null) payment else null,
            // Дальше остаток берём из нового графика: он уже начинается с досрочного платежа.
            currentBalance = null,
        )
        return EarlyResult(
            loan = updated,
            balanceAfter = balance,
            newPayment = payment,
            newEndDate = future.lastOrNull()?.dueDate,
            savedInterest = (oldFutureInterest - future.sumOf { it.interestPart }).coerceAtLeast(0),
        )
    }
}
