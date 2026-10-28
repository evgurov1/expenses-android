package com.evgurov.expenses.ui.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.model.ScheduleItem
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.usecase.LoanMath
import com.evgurov.expenses.importing.ImportDraft
import com.evgurov.expenses.importing.ImportDraftHolder
import com.evgurov.expenses.ui.components.shortTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LoanForm(
    /** Не 0 — редактируем существующий кредит. */
    val id: Long = 0,
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
    val source: LoanSource = LoanSource.MANUAL,
    val note: String = "",
    val importedSchedule: List<ScheduleItem> = emptyList(),
    val importNote: String? = null,
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
    drafts: ImportDraftHolder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editId: Long? = savedStateHandle.get<Long>("loanId")

    private val _form = MutableStateFlow(
        if (editId != null) LoanForm(id = editId) else drafts.take()?.let(::formFromImport) ?: LoanForm(),
    )
    val form: StateFlow<LoanForm> = _form.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                loans.observe(editId).first()?.let { loan -> _form.value = formFromLoan(loan) }
            }
        }
    }

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
                id = f.id,
                type = f.type,
                name = name,
                bank = f.bank.trim(),
                annualRate = parseRate(f.rateText) ?: 0.0,
                creditLimit = limit,
                currentBalance = balance,
                source = f.source,
                note = f.note,
            )
        } else {
            val principal = Money.parse(f.principalText)?.takeIf { it > 0 } ?: error("Укажите сумму кредита")
            val rate = parseRate(f.rateText) ?: error("Укажите ставку, например 18,5")
            val term = f.termText.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: error("Укажите срок в месяцах")
            Loan(
                id = f.id,
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
                source = f.source,
                note = f.note,
                bankSchedule = f.importedSchedule,
            )
        }
    }

    private fun optionalMoney(text: String, message: String): Kopecks? =
        if (text.isBlank()) null else Money.parse(text)?.takeIf { it >= 0 } ?: error(message)
}

private fun plain(amount: Kopecks): String = Money.format(amount).removeSuffix("\u00A0₽")

/** Форма для правки сохранённого кредита: график банка и досрочные платежи сохраняются как есть. */
private fun formFromLoan(loan: Loan) = LoanForm(
    id = loan.id,
    type = loan.type,
    name = loan.name,
    bank = loan.bank,
    principalText = if (loan.principal > 0) plain(loan.principal) else "",
    rateText = if (loan.annualRate > 0) formatRate(loan.annualRate).removeSuffix("%") else "",
    termText = if (loan.termMonths > 0) loan.termMonths.toString() else "",
    issueDate = loan.issueDate,
    paymentType = loan.paymentType,
    paymentText = loan.monthlyPayment?.let(::plain).orEmpty(),
    limitText = loan.creditLimit?.let(::plain).orEmpty(),
    balanceText = loan.currentBalance?.let(::plain).orEmpty(),
    source = loan.source,
    note = loan.note,
    importedSchedule = loan.bankSchedule,
)

private fun formFromImport(draft: ImportDraft): LoanForm {
    val parsed = draft.parsed
    val loan = parsed.toLoan(draft.source)
    val from = if (draft.source == LoanSource.PHOTO) "изображения" else "PDF"
    val found = buildList {
        if (loan.bank.isNotBlank()) add("банк ${loan.bank}")
        parsed.contractNumber?.let { add("договор № $it") }
        parsed.principal?.let { add("сумма ${Money.format(it)}") }
        val debt = parsed.currentDebt
        val remaining = parsed.remainingPayments
        if (parsed.principal == null && remaining != null && debt != null) {
            add("осталось ${Money.format(debt)} за $remaining платежей (в поле суммы — текущий остаток)")
        }
        parsed.nextPaymentDate?.let { add("ближайший платёж ${it.shortTitle()}") }
        loan.bankSchedule.lastOrNull()?.let {
            add("график: ${loan.bankSchedule.size} платежей до ${it.dueDate.shortTitle()}")
        }
        if (parsed.earlyPayments.isNotEmpty()) add("досрочных платежей: ${parsed.earlyPayments.size}")
        parsed.annualRate?.let {
            add(if (parsed.rateIsEstimated) "ставка ≈${formatRate(it)} (оценка, сверьте с договором)" else "ставка ${formatRate(it)}")
        }
    }
    val note = "Распознано из $from: " + found.joinToString(", ").ifBlank { "немного данных" } +
        ". Проверьте поля и дополните недостающие."
    return LoanForm(
        type = loan.type,
        name = loan.name,
        bank = loan.bank,
        principalText = if (loan.principal > 0) plain(loan.principal) else "",
        rateText = if (loan.annualRate > 0) formatRate(loan.annualRate).removeSuffix("%") else "",
        termText = if (loan.termMonths > 0) loan.termMonths.toString() else "",
        issueDate = loan.issueDate,
        paymentText = loan.monthlyPayment?.let(::plain).orEmpty(),
        limitText = loan.creditLimit?.let(::plain).orEmpty(),
        balanceText = loan.currentBalance?.let(::plain).orEmpty(),
        source = draft.source,
        note = loan.note,
        importedSchedule = loan.bankSchedule,
        importNote = note,
    )
}
