package com.evgurov.expenses.bank

import android.content.Context
import com.evgurov.expenses.domain.bank.BankNotificationParser
import com.evgurov.expenses.domain.bank.BankOperation
import com.evgurov.expenses.domain.bank.CategoryGuesser
import com.evgurov.expenses.domain.bank.Statement
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Уведомление банка, в котором не нашли операцию: показываем пользователю, чтобы было видно, что пропущено. */
data class SkippedNotification(val bank: String, val text: String, val date: String)

/** Итог загрузки выписки. */
data class StatementImport(val added: Int, val duplicates: Int, val from: LocalDate, val to: LocalDate)

/** Превращает уведомления банков в операции. Всё хранится только в базе на телефоне. */
@Singleton
class BankImporter @Inject constructor(
    @ApplicationContext context: Context,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) {
    private val prefs = context.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _skipped = MutableStateFlow(loadSkipped())
    val skipped: StateFlow<List<SkippedNotification>> = _skipped.asStateFlow()

    suspend fun onNotification(packageName: String, title: String, text: String, date: LocalDate) = lock.withLock {
        val bank = BankNotificationParser.bankFor(packageName) ?: return@withLock
        val operation = BankNotificationParser.parse(packageName, title, text)
        if (operation == null) {
            // Запоминаем только то, что похоже на операцию с суммой, — рекламу и коды не храним.
            val full = "$title. $text"
            val lower = full.lowercase()
            if (Regex("""\d\s?(₽|р\b|руб|rub)""", RegexOption.IGNORE_CASE).containsMatchIn(full) &&
                listOf("код", "пароль").none { it in lower }
            ) {
                remember(SkippedNotification(bank.accountName, full.take(200), date.toString()))
            }
            return@withLock
        }

        val account = accountId(bank.accountName)
        transactions.insertIfNew(
            Transaction(
                accountId = account,
                categoryId = categoryId(operation),
                amount = operation.amount,
                kind = operation.kind,
                date = date,
                note = operation.merchant,
                // Банк иногда обновляет одно и то же уведомление — по метке не добавим операцию второй раз.
                externalId = "$packageName:$date:${(title + "|" + text).hashCode()}",
            ),
        )
    }

    /**
     * Загружает выписку. Операции, которые уже пришли из уведомлений (та же дата, сумма и направление),
     * и операции из прошлых загрузок той же выписки второй раз не добавляются.
     */
    suspend fun importStatement(statement: Statement): StatementImport = lock.withLock {
        val account = accountId(statement.bank.accountName)
        val from = statement.rows.minOf { it.date }
        val to = statement.rows.maxOf { it.date }
        val fromNotifications = transactions.observeBetween(from, to).first()
            .filter { it.accountId == account && it.externalId?.startsWith("stmt:") != true }
            .toMutableList()
        val seen = mutableMapOf<String, Int>()
        var added = 0
        var duplicates = 0
        for (row in statement.rows) {
            val op = row.operation
            val twin = fromNotifications.firstOrNull { it.date == row.date && it.amount == op.amount && it.kind == op.kind }
            if (twin != null) {
                fromNotifications.remove(twin)
                duplicates++
                continue
            }
            val key = "${row.date}|${row.time}|${op.amount}|${op.kind}|${row.description}"
            val n = seen.merge(key, 1, Int::plus)
            val inserted = transactions.insertIfNew(
                Transaction(
                    accountId = account,
                    categoryId = categoryId(op),
                    amount = op.amount,
                    kind = op.kind,
                    date = row.date,
                    note = row.description,
                    externalId = "stmt:${statement.bank.accountName}:${key.hashCode()}:$n",
                ),
            )
            if (inserted) added++ else duplicates++
        }
        StatementImport(added, duplicates, from, to)
    }

    private suspend fun accountId(name: String): Long =
        accounts.observeAll().first().firstOrNull { it.name == name }?.id
            ?: accounts.upsert(Account(name = name, type = AccountType.CARD))

    private suspend fun categoryId(operation: BankOperation): Long {
        val all = categories.observeAll().first()
        val learned = transactions.merchantRules().mapNotNull { (key, id) ->
            all.firstOrNull { it.id == id && it.kind == operation.kind }?.let { key to it.name }
        }.toMap()
        val name = CategoryGuesser.guess(operation, learned)
        return all.firstOrNull { it.name == name && it.kind == operation.kind }?.id
            ?: categories.upsert(Category(name = name, kind = operation.kind))
    }

    /** Пользователь сменил категорию операции: запоминаем выбор для этого магазина. */
    suspend fun learn(transaction: Transaction, categoryId: Long) {
        transactions.upsert(transaction.copy(categoryId = categoryId))
        val key = CategoryGuesser.merchantKey(transaction.note)
        if (transaction.externalId != null && key.isNotEmpty()) transactions.saveMerchantRule(key, categoryId)
    }

    fun clearSkipped() {
        _skipped.value = emptyList()
        prefs.edit().remove(SKIPPED).apply()
    }

    private fun remember(item: SkippedNotification) {
        val list = (listOf(item) + _skipped.value).distinctBy { it.text }.take(MAX_SKIPPED)
        _skipped.value = list
        val json = JSONArray()
        list.forEach { json.put(JSONArray(listOf(it.bank, it.text, it.date))) }
        prefs.edit().putString(SKIPPED, json.toString()).apply()
    }

    private fun loadSkipped(): List<SkippedNotification> = runCatching {
        val json = JSONArray(prefs.getString(SKIPPED, "[]"))
        (0 until json.length()).map { i ->
            val row = json.getJSONArray(i)
            SkippedNotification(row.getString(0), row.getString(1), row.getString(2))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val SKIPPED = "skipped"
        const val MAX_SKIPPED = 20
    }
}
