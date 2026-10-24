package com.evgurov.expenses.domain.usecase

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Money
import java.time.LocalDate

/** Названия кредитов для советов и чата. Одинаковые названия различаем по остатку или платежу. */
object LoanLabels {
    fun of(loans: List<Loan>, today: LocalDate): Map<Loan, String> {
        val duplicated = loans.groupBy { it.name.trim().lowercase() }.filterValues { it.size > 1 }.keys
        return loans.associateWith { loan ->
            if (loan.name.trim().lowercase() !in duplicated) return@associateWith "«${loan.name}»"
            val balance = LoanMath.balanceOn(loan, today)
            val hint = if (balance > 0) "остаток ${Money.format(balance)}" else "платёж ${Money.format(LoanMath.regularPayment(loan))}"
            "«${loan.name}» ($hint)"
        }
    }
}
