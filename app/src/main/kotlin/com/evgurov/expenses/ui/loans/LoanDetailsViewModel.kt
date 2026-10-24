package com.evgurov.expenses.ui.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.ScheduleItem
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.usecase.LoanMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LoanDetails(
    val loan: Loan,
    val payment: Kopecks,
    val balance: Kopecks,
    val schedule: List<ScheduleItem>,
    val remainingInterest: Kopecks,
    val today: LocalDate,
)

@HiltViewModel
class LoanDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loans: LoanRepository,
) : ViewModel() {

    private val loanId: Long = checkNotNull(savedStateHandle["loanId"])

    val details: StateFlow<LoanDetails?> = loans.observe(loanId)
        .map { loan ->
            loan?.let {
                val today = LocalDate.now()
                val schedule = LoanMath.schedule(it)
                LoanDetails(
                    loan = it,
                    payment = LoanMath.regularPayment(it),
                    balance = LoanMath.balanceOn(it, today),
                    schedule = schedule,
                    remainingInterest = schedule.filter { item -> item.dueDate.isAfter(today) }.sumOf { item -> item.interestPart },
                    today = today,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(loan: Loan) {
        viewModelScope.launch { loans.upsert(loan) }
    }

    fun delete(onDeleted: () -> Unit) {
        val loan = details.value?.loan ?: return
        viewModelScope.launch {
            loans.delete(loan)
            onDeleted()
        }
    }
}
