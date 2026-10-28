package com.evgurov.expenses.domain.repository

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun upsert(account: Account): Long
    suspend fun delete(account: Account)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun upsert(category: Category): Long
    suspend fun delete(category: Category)
}

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>>
    suspend fun upsert(transaction: Transaction): Long
    suspend fun delete(transaction: Transaction)

    /** Добавляет операцию, если операции с тем же [Transaction.externalId] ещё нет. */
    suspend fun insertIfNew(transaction: Transaction): Boolean

    /** Ключ магазина → id категории, которую выбрал пользователь. */
    suspend fun merchantRules(): Map<String, Long>
    suspend fun saveMerchantRule(merchantKey: String, categoryId: Long)
}

interface LoanRepository {
    fun observeAll(): Flow<List<Loan>>
    fun observe(id: Long): Flow<Loan?>

    /** Погашенные кредиты; [observeAll] их не возвращает. */
    fun observeClosed(): Flow<List<Loan>>

    /** Отметить кредит погашенным в [date] или, если null, вернуть в действующие. */
    suspend fun setClosed(id: Long, date: LocalDate?)
    suspend fun upsert(loan: Loan): Long
    suspend fun delete(loan: Loan)
}

interface IncomeRepository {
    fun observeAll(): Flow<List<IncomeSource>>
    suspend fun upsert(source: IncomeSource): Long
    suspend fun delete(source: IncomeSource)
}
