package com.evgurov.expenses.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.usecase.LoanMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LoanForm(
    val type: LoanType = LoanType.CONSUMER,
    val name: String = "",
    val bank: String = "",
    val principalText: String = "",
    val rateText: String = "",
    val termText: String = "",
    val issueDate: LocalDate = LocalDate.now(),
    val paymentType: PaymentType = PaymentType.ANNUITY,
    val paymentText: String = "",
    val limitText: String = "",
    val balanceText: String = "",
    val error: String? = null,
    val saved: Boolean = false,
) {
    val isCard: Boolean get() = type == LoanType.CREDIT_CARD

    /** Платёж, который мы бы посчитали сами, — подсказка рядом с полем «Платёж». */
    val calculatedPayment: Kopecks?
        get() {
            if (isCard) return Money.parse(limitText)?.let { (it * LoanMath.CARD_LIMIT_SHARE).toLong() }
            val principal = Money.parse(principalText)?.takeIf { it > 0 } ?: return null
            val rate = parseRate(rateText) ?: return null
            val term = termText.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: return null
            return LoanMath.regularPayment(
                Loan(type = type, name = "", principal = principal, annualRate = rate, termMonths = term, paymentType = paymentType),
            )
        }
}

@HiltViewModel
class AddLoanViewModel @Inject constructor(
    private val loans: LoanRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(LoanForm())
    val form: StateFlow<LoanForm> = _form.asStateFlow()

    fun edit(change: (LoanForm) -> LoanForm) = _form.update { change(it).copy(error = null) }

    fun save() {
        val f = _form.value
        val result = buildLoan(f)
        result.onFailure { e -> _form.update { it.copy(error = e.message) } }
        result.onSuccess { loan ->
            viewModelScope.launch {
                loans.upsert(loan)
                _form.update { it.copy(saved = true) }
            }
        }
    }

    private fun buildLoan(f: LoanForm): Result<Loan> = runCatching {
        val name = f.name.trim().ifBlank { listOf(f.type.label, f.bank.trim()).filter { it.isNotBlank() }.joinToString(", ") }
        val balance = optionalMoney(f.balanceText, "Остаток долга указан неверно")
        if (f.isCard) {
            val limit = Money.parse(f.limitText)?.takeIf { it > 0 } ?: error("Укажите лимит карты")
            Loan(
                type = f.type,
                name = name,
                bank = f.bank.trim(),
                annualRate = parseRate(f.rateText) ?: 0.0,
                creditLimit = limit,
                currentBalance = balance,
            )
        } else {
            val principal = Money.parse(f.principalText)?.takeIf { it > 0 } ?: error("Укажите сумму кредита")
            val rate = parseRate(f.rateText) ?: error("Укажите ставку, например 18,5")
            val term = f.termText.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: error("Укажите срок в месяцах")
            Loan(
                type = f.type,
                name = name,
                bank = f.bank.trim(),
                principal = principal,
                annualRate = rate,
                termMonths = term,
                issueDate = f.issueDate,
                paymentType = f.paymentType,
                monthlyPayment = optionalMoney(f.paymentText, "Платёж указан неверно"),
                currentBalance = balance,
            )
        }
    }

    private fun optionalMoney(text: String, message: String): Kopecks? =
        if (text.isBlank()) null else Money.parse(text)?.takeIf { it >= 0 } ?: error(message)
}
