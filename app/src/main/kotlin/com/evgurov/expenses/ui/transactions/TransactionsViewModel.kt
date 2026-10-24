package com.evgurov.expenses.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.bank.BankImporter
import com.evgurov.expenses.bank.SkippedNotification
import com.evgurov.expenses.domain.bank.StatementParser
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.importing.LoanTextExtractor
import android.net.Uri
import kotlinx.coroutines.flow.asStateFlow
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.PeriodSummary
import com.evgurov.expenses.domain.usecase.summarize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TransactionRow(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
)

data class DayGroup(val date: LocalDate, val rows: List<TransactionRow>)

data class TransactionsUiState(
    val month: YearMonth = YearMonth.now(),
    val summary: PeriodSummary = PeriodSummary(0, 0),
    val days: List<DayGroup> = emptyList(),
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val importer: BankImporter,
    private val extractor: LoanTextExtractor,
    categories: CategoryRepository,
    accounts: AccountRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<TransactionsUiState> = month
        .flatMapLatest { m ->
            combine(
                transactions.observeBetween(m.atDay(1), m.atEndOfMonth()),
                categories.observeAll(),
                accounts.observeAll(),
            ) { list, cats, accs ->
                val categoryNames = cats.associate { it.id to it.name }
                val accountNames = accs.associate { it.id to it.name }
                TransactionsUiState(
                    month = m,
                    summary = summarize(list),
                    days = list
                        .map { TransactionRow(it, categoryNames[it.categoryId].orEmpty(), accountNames[it.accountId].orEmpty()) }
                        .groupBy { it.transaction.date }
                        .map { (date, rows) -> DayGroup(date, rows) },
                    loading = false,
                    categories = cats,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    /** Уведомления банков, в которых не нашли операцию. */
    val skipped: StateFlow<List<SkippedNotification>> = importer.skipped

    fun clearSkipped() = importer.clearSkipped()

    private val _statementStatus = MutableStateFlow<String?>(null)

    /** Что происходит с выпиской: «Читаю…» или итог загрузки. */
    val statementStatus: StateFlow<String?> = _statementStatus.asStateFlow()

    fun importStatement(uri: Uri) {
        _statementStatus.value = "Читаю выписку…"
        viewModelScope.launch {
            _statementStatus.value = runCatching {
                val statement = StatementParser.parse(extractor.fromPdf(uri))
                    ?: return@runCatching "Не похоже на выписку по карте. Сейчас я понимаю выписку и справку о движении средств Т-Банка в PDF."
                val result = importer.importStatement(statement)
                month.value = YearMonth.from(result.to)
                buildString {
                    append("Выписка ${statement.bank.accountName} с ${result.from.format(DAY)} по ${result.to.format(DAY)}: ")
                    append("добавлено операций — ${result.added}")
                    if (result.duplicates > 0) append(", уже были — ${result.duplicates}")
                    append(".")
                }
            }.getOrElse { "Не удалось прочитать файл: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    /** Смена категории; для операций из банка приложение запоминает выбор для этого магазина. */
    fun changeCategory(transaction: Transaction, categoryId: Long) {
        viewModelScope.launch { importer.learn(transaction, categoryId) }
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch { transactions.delete(transaction) }
    }
}

private val DAY = DateTimeFormatter.ofPattern("dd.MM.yy")
