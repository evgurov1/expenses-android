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
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.DayOfWeek
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

enum class PeriodMode(val label: String) { WEEK("Неделя"), MONTH("Месяц"), YEAR("Год") }

/** Выбранный период: неделя, месяц или год, в который попадает [anchor]. */
data class Period(val mode: PeriodMode, val anchor: LocalDate) {
    val from: LocalDate
        get() = when (mode) {
            PeriodMode.WEEK -> anchor.with(DayOfWeek.MONDAY)
            PeriodMode.MONTH -> anchor.withDayOfMonth(1)
            PeriodMode.YEAR -> anchor.withDayOfYear(1)
        }
    val to: LocalDate
        get() = when (mode) {
            PeriodMode.WEEK -> from.plusDays(6)
            PeriodMode.MONTH -> YearMonth.from(anchor).atEndOfMonth()
            PeriodMode.YEAR -> from.plusYears(1).minusDays(1)
        }

    fun shift(step: Long) = copy(
        anchor = when (mode) {
            PeriodMode.WEEK -> anchor.plusWeeks(step)
            PeriodMode.MONTH -> anchor.plusMonths(step)
            PeriodMode.YEAR -> anchor.plusYears(step)
        },
    )
}

/** Доля категории на диаграмме; «Остальное» объединяет несколько категорий. */
data class CategorySlice(val name: String, val amount: Long, val categoryIds: Set<Long>, val isRest: Boolean = false)

data class TransactionsUiState(
    val period: Period = Period(PeriodMode.MONTH, LocalDate.now()),
    val summary: PeriodSummary = PeriodSummary(0, 0),
    val days: List<DayGroup> = emptyList(),
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val chartKind: TransactionKind = TransactionKind.EXPENSE,
    val slices: List<CategorySlice> = emptyList(),
    /** Категория, по которой отфильтрован список, или null. */
    val selected: CategorySlice? = null,
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

    private val period = MutableStateFlow(Period(PeriodMode.MONTH, LocalDate.now()))
    private val chartKind = MutableStateFlow(TransactionKind.EXPENSE)
    private val selectedName = MutableStateFlow<String?>(null)

    val state: StateFlow<TransactionsUiState> = period
        .flatMapLatest { p ->
            combine(
                transactions.observeBetween(p.from, p.to),
                categories.observeAll(),
                accounts.observeAll(),
                chartKind,
                selectedName,
            ) { list, cats, accs, kind, chosen ->
                val categoryNames = cats.associate { it.id to it.name }
                val accountNames = accs.associate { it.id to it.name }
                val slices = categorySlices(list.filter { it.kind == kind }, categoryNames)
                val selected = slices.firstOrNull { it.name == chosen }
                val shown = if (selected == null) list else list.filter { it.kind == kind && it.categoryId in selected.categoryIds }
                TransactionsUiState(
                    period = p,
                    summary = summarize(list),
                    days = shown
                        .map { TransactionRow(it, categoryNames[it.categoryId].orEmpty(), accountNames[it.accountId].orEmpty()) }
                        .groupBy { it.transaction.date }
                        .map { (date, rows) -> DayGroup(date, rows) },
                    loading = false,
                    categories = cats,
                    chartKind = kind,
                    slices = slices,
                    selected = selected,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun previousPeriod() = period.update { it.shift(-1) }

    fun nextPeriod() = period.update { it.shift(1) }

    fun setPeriodMode(mode: PeriodMode) = period.update { it.copy(mode = mode) }

    fun setChartKind(kind: TransactionKind) {
        chartKind.value = kind
        selectedName.value = null
    }

    /** Нажатие на категорию под диаграммой: показать только её операции, повторное — снять фильтр. */
    fun toggleSlice(slice: CategorySlice) = selectedName.update { if (it == slice.name) null else slice.name }

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
                    ?: return@runCatching "Не похоже на выписку по карте. Сейчас я понимаю выписку по договору Т-Банка в PDF. Справку о движении средств не загружаю: в ней траты задваиваются."
                val result = importer.importStatement(statement)
                period.value = Period(PeriodMode.MONTH, result.to)
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

/** Сколько категорий показываем отдельно; остальные идут в «Остальное». */
const val MAX_SLICES = 6

/** Суммы по категориям по убыванию: первые [MAX_SLICES] отдельно, остальные одной долей. */
fun categorySlices(list: List<Transaction>, categoryNames: Map<Long, String>): List<CategorySlice> {
    val byCategory = list.groupBy { it.categoryId }
        .map { (id, items) -> CategorySlice(categoryNames[id] ?: "Без категории", items.sumOf { it.amount }, setOf(id)) }
        .sortedByDescending { it.amount }
    if (byCategory.size <= MAX_SLICES + 1) return byCategory
    val rest = byCategory.drop(MAX_SLICES)
    return byCategory.take(MAX_SLICES) +
        CategorySlice("Остальное", rest.sumOf { it.amount }, rest.flatMap { it.categoryIds }.toSet(), isRest = true)
}
