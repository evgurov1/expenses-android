package com.evgurov.expenses.data.repository

import com.evgurov.expenses.data.db.AccountDao
import com.evgurov.expenses.data.db.CategoryDao
import com.evgurov.expenses.data.db.TransactionDao
import com.evgurov.expenses.data.db.toDomain
import com.evgurov.expenses.data.db.toEntity
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(private val dao: AccountDao) : AccountRepository {
    override fun observeAll(): Flow<List<Account>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(account: Account): Long = dao.upsert(account.toEntity())
    override suspend fun delete(account: Account) = dao.delete(account.toEntity())
}

class RoomCategoryRepository @Inject constructor(private val dao: CategoryDao) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(category: Category): Long = dao.upsert(category.toEntity())
    override suspend fun delete(category: Category) = dao.delete(category.toEntity())
}

class RoomTransactionRepository @Inject constructor(private val dao: TransactionDao) : TransactionRepository {
    override fun observeAll(): Flow<List<Transaction>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Transaction>> =
        dao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(transaction: Transaction): Long = dao.upsert(transaction.toEntity())
    override suspend fun delete(transaction: Transaction) = dao.delete(transaction.toEntity())
}
