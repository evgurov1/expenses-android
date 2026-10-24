package com.evgurov.expenses.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import androidx.lifecycle.SavedStateHandle
import com.evgurov.expenses.domain.bank.CategoryGuesser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TransactionForm(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val error: String? = null,
    val saved: Boolean = false,
    /** Правим существующую операцию, а не создаём новую. */
    val editing: Transaction? = null,
)

data class AddTransactionUiState(
    val form: TransactionForm = TransactionForm(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    /** Если пользователь ещё ничего не выбрал, подставляем первый счёт. */
    val selectedAccountId: Long? get() = form.accountId ?: accounts.firstOrNull()?.id
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val form = MutableStateFlow(TransactionForm())

    init {
        savedState.get<Long>("transactionId")?.let { id ->
            viewModelScope.launch {
                val t = transactions.observeAll().first().firstOrNull { it.id == id } ?: return@launch
                form.value = TransactionForm(
                    kind = t.kind,
                    amountText = Money.format(t.amount, currencySymbol = "").trim(),
                    accountId = t.accountId,
                    categoryId = t.categoryId,
                    date = t.date,
                    note = t.note,
                    editing = t,
                )
            }
        }
    }

    val state: StateFlow<AddTransactionUiState> = combine(
        form,
        accounts.observeAll(),
        categories.observeAll(),
    ) { f, accs, cats ->
        AddTransactionUiState(form = f, accounts = accs, categories = cats.filter { it.kind == f.kind })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTransactionUiState())

    fun setKind(kind: TransactionKind) = form.update { it.copy(kind = kind, categoryId = null, error = null) }
    fun setAmount(text: String) = form.update { it.copy(amountText = text, error = null) }
    fun setAccount(id: Long) = form.update { it.copy(accountId = id, error = null) }
    fun setCategory(id: Long) = form.update { it.copy(categoryId = id, error = null) }
    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }
    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun delete() {
        val t = form.value.editing ?: return
        viewModelScope.launch {
            transactions.delete(t)
            form.update { it.copy(saved = true) }
        }
    }

    fun save() {
        val current = state.value
        val f = current.form
        val amount = Money.parse(f.amountText)
        val accountId = current.selectedAccountId
        val error = when {
            amount == null || amount <= 0 -> "Введите сумму больше нуля"
            accountId == null -> "Сначала создайте счёт"
            f.categoryId == null -> "Выберите категорию"
            else -> null
        }
        if (error != null) {
            form.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            val old = f.editing
            if (old != null) {
                transactions.upsert(
                    old.copy(
                        accountId = accountId!!,
                        categoryId = f.categoryId!!,
                        amount = amount!!,
                        kind = f.kind,
                        date = f.date,
                        note = f.note.trim(),
                    ),
                )
                // Новая категория для операции из банка или с фото — запоминаем её для этого магазина.
                val merchant = CategoryGuesser.merchantKey(f.note)
                if (old.externalId != null && old.categoryId != f.categoryId && merchant.isNotEmpty()) {
                    transactions.saveMerchantRule(merchant, f.categoryId)
                }
                form.update { it.copy(saved = true) }
                return@launch
            }
            transactions.upsert(
                Transaction(
                    accountId = accountId!!,
                    categoryId = f.categoryId!!,
                    amount = amount!!,
                    kind = f.kind,
                    date = f.date,
                    note = f.note.trim(),
                ),
            )
            form.update { it.copy(saved = true) }
        }
    }
}
