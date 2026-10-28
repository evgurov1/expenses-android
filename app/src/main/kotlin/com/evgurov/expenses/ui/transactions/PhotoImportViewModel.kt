package com.evgurov.expenses.ui.transactions

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.bank.BankNotificationParser
import com.evgurov.expenses.domain.bank.CategoryGuesser
import com.evgurov.expenses.domain.bank.ScreenshotParser
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.importing.LoanTextExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Операция, найденная на фото: пользователь проверяет её и при необходимости правит перед сохранением. */
data class PhotoDraft(
    val id: Int,
    val date: LocalDate,
    val name: String,
    val amount: Kopecks,
    val kind: TransactionKind,
    /** Название категории приложения; null — приложение не знает и просит выбрать. */
    val category: String?,
    val card: String?,
    val selected: Boolean,
    /** Почему операция не отмечена: «уже есть», «перевод себе». */
    val hint: String? = null,
    /** Пользователь сам выбрал категорию — запомним её для этого магазина. */
    val categoryChosen: Boolean = false,
)

data class PhotoImportUiState(
    val status: String? = null,
    val busy: Boolean = false,
    val drafts: List<PhotoDraft> = emptyList(),
    val saved: Int? = null,
) {
    val toSave: List<PhotoDraft> get() = drafts.filter { it.selected }
    val withoutCategory: Int get() = toSave.count { it.category == null }
}

@HiltViewModel
class PhotoImportViewModel @Inject constructor(
    private val extractor: LoanTextExtractor,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotoImportUiState())
    val state: StateFlow<PhotoImportUiState> = _state.asStateFlow()

    val allCategories: StateFlow<List<Category>> =
        categories.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextId = 0

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(busy = true, status = null) }
        viewModelScope.launch {
            val today = LocalDate.now()
            val learned = learnedNames()
            val existing = transactions.observeAll().first()
            var failed = 0
            val found = mutableListOf<PhotoDraft>()
            uris.forEachIndexed { index, uri ->
                _state.update { it.copy(status = "Распознаю фото ${index + 1} из ${uris.size}…") }
                val rows = runCatching { ScreenshotParser.parse(extractor.fromScreenshot(uri), today) }.getOrElse { emptyList() }
                if (rows.isEmpty()) failed++
                // Одну и ту же операцию на двух пересекающихся скриншотах берём один раз.
                val known = (_state.value.drafts + found).groupingBy { key(it.date, it.name, it.amount, it.kind) }.eachCount()
                val onThis = mutableMapOf<String, Int>()
                for (row in rows) {
                    val k = key(row.date, row.name, row.amount, row.kind)
                    val n = onThis.merge(k, 1, Int::plus)!!
                    if (n <= (known[k] ?: 0)) continue
                    val duplicate = existing.any { it.date == row.date && it.amount == row.amount && it.kind == row.kind }
                    val own = BankNotificationParser.isOwnTransfer(row.name)
                    found += PhotoDraft(
                        id = nextId++,
                        date = row.date,
                        name = row.name,
                        amount = row.amount,
                        kind = row.kind,
                        category = guessCategory(row.name, row.kind, row.bankCategory, learned),
                        card = row.card,
                        selected = !duplicate && !own,
                        hint = when {
                            duplicate -> "Уже есть в приложении"
                            own -> "Перевод себе — не трата"
                            else -> null
                        },
                    )
                }
            }
            _state.update { s ->
                s.copy(
                    busy = false,
                    drafts = (s.drafts + found).sortedByDescending { it.date },
                    status = when {
                        found.isEmpty() -> "Не нашёл операций на фото. Подойдёт скриншот списка операций из приложения банка."
                        failed > 0 -> "На $failed фото не нашёл операций."
                        else -> null
                    },
                )
            }
        }
    }

    fun update(draft: PhotoDraft) = _state.update { s -> s.copy(drafts = s.drafts.map { if (it.id == draft.id) draft else it }) }

    fun toggle(id: Int) = _state.update { s -> s.copy(drafts = s.drafts.map { if (it.id == id) it.copy(selected = !it.selected) else it }) }

    fun save() {
        val drafts = _state.value.toSave
        if (drafts.isEmpty() || drafts.any { it.category == null }) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val cats = categories.observeAll().first().toMutableList()
            val accs = accounts.observeAll().first().toMutableList()
            val seen = mutableMapOf<String, Int>()
            var added = 0
            for (d in drafts) {
                val categoryName = d.category ?: continue
                val categoryId = cats.firstOrNull { it.name == categoryName && it.kind == d.kind }?.id
                    ?: categories.upsert(Category(name = categoryName, kind = d.kind)).also { cats += Category(it, categoryName, d.kind) }
                val accountName = d.card ?: DEFAULT_ACCOUNT
                val accountId = accs.firstOrNull { it.name.equals(accountName, ignoreCase = true) }?.id
                    ?: accounts.upsert(Account(name = accountName, type = AccountType.CARD)).also {
                        accs += Account(id = it, name = accountName, type = AccountType.CARD)
                    }
                val k = key(d.date, d.name, d.amount, d.kind)
                val n = seen.merge(k, 1, Int::plus)
                val inserted = transactions.insertIfNew(
                    Transaction(
                        accountId = accountId,
                        categoryId = categoryId,
                        amount = d.amount,
                        kind = d.kind,
                        date = d.date,
                        note = d.name,
                        externalId = "photo:${k.hashCode()}:$n",
                    ),
                )
                if (inserted) added++
                val merchant = CategoryGuesser.merchantKey(d.name)
                if (d.categoryChosen && merchant.isNotEmpty()) transactions.saveMerchantRule(merchant, categoryId)
            }
            _state.update { it.copy(busy = false, saved = added) }
        }
    }

    /** Выбор пользователя по магазинам: ключ магазина → название категории (с учётом расхода или дохода). */
    private suspend fun learnedNames(): Map<Pair<String, TransactionKind>, String> {
        val cats = categories.observeAll().first()
        return transactions.merchantRules().mapNotNull { (key, id) ->
            cats.firstOrNull { it.id == id }?.let { (key to it.kind) to it.name }
        }.toMap()
    }

    private fun guessCategory(
        name: String,
        kind: TransactionKind,
        bankCategory: String?,
        learned: Map<Pair<String, TransactionKind>, String>,
    ): String? {
        learned[CategoryGuesser.merchantKey(name) to kind]?.let { return it }
        if (kind == TransactionKind.EXPENSE) CategoryGuesser.byMerchant(name)?.let { return it }
        val fromBank = ScreenshotParser.categoryFor(bankCategory)
        return when (kind) {
            TransactionKind.EXPENSE -> fromBank?.takeIf { it !in INCOME_ONLY }
            TransactionKind.INCOME -> fromBank?.takeIf { it in INCOME_ONLY || it == CategoryGuesser.TRANSFERS }
        }
    }

    private fun key(date: LocalDate, name: String, amount: Kopecks, kind: TransactionKind) = "$date|${name.lowercase()}|$amount|$kind"

    private companion object {
        const val DEFAULT_ACCOUNT = "Карта"
        val INCOME_ONLY = setOf("Зарплата", "Проценты и кешбэк")
    }
}
