package com.evgurov.expenses.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Delete
    suspend fun delete(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}
