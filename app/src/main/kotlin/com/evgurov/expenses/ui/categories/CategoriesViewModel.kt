package com.evgurov.expenses.ui.categories

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val expense: List<Category> = emptyList(),
    val income: List<Category> = emptyList(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<CategoriesUiState> = categories.observeAll()
        .map { list ->
            CategoriesUiState(
                expense = list.filter { it.kind == TransactionKind.EXPENSE },
                income = list.filter { it.kind == TransactionKind.INCOME },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun add(name: String, kind: TransactionKind) {
        viewModelScope.launch { categories.upsert(Category(name = name.trim(), kind = kind)) }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            try {
                categories.delete(category)
            } catch (e: SQLiteConstraintException) {
                _messages.send("Категория «${category.name}» используется в операциях")
            }
        }
    }
}
