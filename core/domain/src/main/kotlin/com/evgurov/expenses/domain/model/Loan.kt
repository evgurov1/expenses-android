package com.evgurov.expenses.domain.model

import java.time.LocalDate

enum class LoanType { CONSUMER, MORTGAGE, CAR, CREDIT_CARD, MICROLOAN, OTHER }

enum class PaymentType { ANNUITY, DIFFERENTIATED }

enum class LoanSource { MANUAL, PHOTO, CONTRACT }

/**
 * Кредит. Для кредитной карты важен [creditLimit], а [principal], срок и график не используются.
 *
 * @param issueDate дата выдачи; первый платёж через месяц после неё.
 * @param monthlyPayment платёж из договора; если null, считаем сами по сумме, ставке и сроку.
 * @param currentBalance остаток долга, если пользователь знает его точнее, чем наш график.
 * @param bankSchedule график из документа банка; если он есть, используем его вместо расчётного.
 * @param closedOn дата, когда пользователь отметил кредит погашенным; такой кредит не входит в долг, ПДН и напоминания.
 */
data class Loan(
    val id: Long = 0,
    val type: LoanType,
    val name: String,
    val bank: String = "",
    val principal: Kopecks = 0,
    val annualRate: Double = 0.0,
    val termMonths: Int = 0,
    val issueDate: LocalDate = LocalDate.now(),
    val paymentType: PaymentType = PaymentType.ANNUITY,
    val monthlyPayment: Kopecks? = null,
    val creditLimit: Kopecks? = null,
    val currentBalance: Kopecks? = null,
    val source: LoanSource = LoanSource.MANUAL,
    val note: String = "",
    val bankSchedule: List<ScheduleItem> = emptyList(),
    val closedOn: LocalDate? = null,
) {
    val isCard: Boolean get() = type == LoanType.CREDIT_CARD
}

data class IncomeSource(
    val id: Long = 0,
    val name: String,
    val monthlyAmount: Kopecks,
)

data class ScheduleItem(
    val number: Int,
    val dueDate: LocalDate,
    val payment: Kopecks,
    val principalPart: Kopecks,
    val interestPart: Kopecks,
    val balanceAfter: Kopecks,
    /** Досрочный платёж, который внёс пользователь; в регулярные платежи и ПДН не входит. */
    val early: Boolean = false,
)
