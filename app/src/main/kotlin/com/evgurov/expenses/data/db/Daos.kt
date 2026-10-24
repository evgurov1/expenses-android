package com.evgurov.expenses.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

@Dao
abstract class LoanDao {
    @Transaction
    @Query("SELECT * FROM loans ORDER BY id")
    abstract fun observeAll(): Flow<List<LoanWithPayments>>

    @Transaction
    @Query("SELECT * FROM loans WHERE id = :id")
    abstract fun observe(id: Long): Flow<LoanWithPayments?>

    @Upsert
    abstract suspend fun upsertLoan(loan: LoanEntity): Long

    @Insert
    abstract suspend fun insertPayments(payments: List<LoanPaymentEntity>)

    @Query("DELETE FROM loan_payments WHERE loanId = :loanId")
    abstract suspend fun deletePayments(loanId: Long)

    /** Сохраняет кредит и заменяет его график целиком. */
    @Transaction
    open suspend fun save(loan: LoanEntity, payments: List<LoanPaymentEntity>): Long {
        val inserted = upsertLoan(loan)
        val id = if (loan.id != 0L) loan.id else inserted
        deletePayments(id)
        insertPayments(payments.map { it.copy(loanId = id) })
        return id
    }

    @Delete
    abstract suspend fun delete(loan: LoanEntity)
}

@Dao
interface IncomeSourceDao {
    @Query("SELECT * FROM income_sources ORDER BY id")
    fun observeAll(): Flow<List<IncomeSourceEntity>>

    @Upsert
    suspend fun upsert(source: IncomeSourceEntity): Long

    @Delete
    suspend fun delete(source: IncomeSourceEntity)
}
