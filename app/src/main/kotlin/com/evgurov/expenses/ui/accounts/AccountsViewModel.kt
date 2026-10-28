package com.evgurov.expenses.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.accountBalances
import com.evgurov.expenses.domain.usecase.withBalanceNow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountWithBalance(val account: Account, val balance: Kopecks)

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
) {
    val total: Kopecks get() = accounts.sumOf { it.balance }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    val state: StateFlow<AccountsUiState> = combine(
        accounts.observeAll(),
        transactions.observeAll(),
    ) { accs, list ->
        val balances = accountBalances(accs, list)
        AccountsUiState(accs.map { AccountWithBalance(it, balances[it.id] ?: it.initialBalance) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun add(name: String, type: AccountType, initialBalance: Kopecks) {
        viewModelScope.launch {
            accounts.upsert(Account(name = name.trim(), type = type, initialBalance = initialBalance))
        }
    }

    /** Остаток «сейчас», введённый пользователем. */
    fun setBalance(account: Account, amount: Kopecks) {
        viewModelScope.launch {
            val today = LocalDate.now()
            accounts.upsert(account.withBalanceNow(amount, today, transactions.observeBetween(today, today).first()))
        }
    }

    fun delete(account: Account) {
        viewModelScope.launch { accounts.delete(account) }
    }
}
