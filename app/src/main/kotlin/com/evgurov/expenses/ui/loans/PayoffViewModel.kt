package com.evgurov.expenses.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.PayoffAdvice
import com.evgurov.expenses.domain.usecase.PayoffPlanner
import com.evgurov.expenses.domain.usecase.PayoffStrategy
import com.evgurov.expenses.domain.usecase.monthlyIncome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class PayoffInput(
    val extraText: String = "10 000",
    val lumpText: String = "",
    val strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
)

data class PayoffUiState(
    val input: PayoffInput = PayoffInput(),
    val advice: PayoffAdvice? = null,
    val hasLoans: Boolean = true,
)

@HiltViewModel
class PayoffViewModel @Inject constructor(
    loans: LoanRepository,
    incomes: IncomeRepository,
    transactions: TransactionRepository,
) : ViewModel() {

    private val input = MutableStateFlow(PayoffInput())

    val state: StateFlow<PayoffUiState> = combine(
        loans.observeAll(),
        incomes.observeAll(),
        transactions.observeAll(),
        input,
    ) { loanList, sources, txs, form ->
        val today = LocalDate.now()
        val income = monthlyIncome(sources, txs, YearMonth.from(today)).amount
        val advice = PayoffPlanner.advise(
            loans = loanList,
            extraMonthly = money(form.extraText),
            lumpSum = money(form.lumpText),
            strategy = form.strategy,
            income = income,
            today = today,
        )
        PayoffUiState(input = form, advice = advice, hasLoans = advice.order.isNotEmpty())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayoffUiState())

    fun edit(change: (PayoffInput) -> PayoffInput) = input.update(change)

    private fun money(text: String): Kopecks = Money.parse(text)?.coerceAtLeast(0) ?: 0
}
