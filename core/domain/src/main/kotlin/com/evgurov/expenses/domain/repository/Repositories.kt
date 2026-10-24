package com.evgurov.expenses.domain.repository

import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.Category
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
}
