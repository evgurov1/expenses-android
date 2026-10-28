package com.evgurov.expenses.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.BurdenChange
import com.evgurov.expenses.domain.usecase.DebtBurden
import com.evgurov.expenses.domain.usecase.IncomeBasis
import com.evgurov.expenses.domain.usecase.LoanMath
import com.evgurov.expenses.domain.usecase.burdenForecast
import com.evgurov.expenses.domain.usecase.debtBurden
import com.evgurov.expenses.domain.usecase.monthlyIncome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import com.evgurov.expenses.settings.PaydayStore
import javax.inject.Inject

data class LoanRow(
    val loan: Loan,
    val payment: Kopecks,
    val balance: Kopecks,
    val endDate: LocalDate?,
)

data class LoansUiState(
    val loans: List<LoanRow> = emptyList(),
    val burden: DebtBurden = DebtBurden(0, 0),
    val incomeBasis: IncomeBasis = IncomeBasis.NONE,
    val incomeSources: List<IncomeSource> = emptyList(),
    val nextChange: BurdenChange? = null,
    val loading: Boolean = true,
    val closed: List<Loan> = emptyList(),
) {
    val totalDebt: Kopecks get() = loans.sumOf { it.balance }
}

@HiltViewModel
class LoansViewModel @Inject constructor(
    loans: LoanRepository,
    private val incomes: IncomeRepository,
    transactions: TransactionRepository,
    private val paydayStore: PaydayStore,
) : ViewModel() {

    val paydays: StateFlow<List<Int>> = paydayStore.paydays

    fun setPaydays(days: List<Int>) = paydayStore.set(days)

    val state: StateFlow<LoansUiState> = combine(
        loans.observeAll(),
        incomes.observeAll(),
        transactions.observeAll(),
        loans.observeClosed(),
    ) { loanList, sources, txs, closed ->
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val income = monthlyIncome(sources, txs, month)
        val active = loanList.filter { it.isCard || LoanMath.balanceOn(it, today) > 0 }
        LoansUiState(
            loans = loanList.map {
                LoanRow(it, LoanMath.regularPayment(it), LoanMath.balanceOn(it, today), LoanMath.endDate(it))
            },
            burden = debtBurden(active, income.amount, month),
            incomeBasis = income.basis,
            incomeSources = sources,
            nextChange = burdenForecast(active, income.amount, month).firstOrNull(),
            loading = false,
            closed = closed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansUiState())

    fun addIncome(name: String, amount: Kopecks) {
        viewModelScope.launch { incomes.upsert(IncomeSource(name = name.trim(), monthlyAmount = amount)) }
    }

    fun deleteIncome(source: IncomeSource) {
        viewModelScope.launch { incomes.delete(source) }
    }
}
