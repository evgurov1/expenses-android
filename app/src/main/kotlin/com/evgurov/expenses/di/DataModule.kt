package com.evgurov.expenses.di

import android.content.Context
import androidx.room.Room
import com.evgurov.expenses.data.db.ExpensesDatabase
import com.evgurov.expenses.data.repository.RoomAccountRepository
import com.evgurov.expenses.data.repository.RoomCategoryRepository
import com.evgurov.expenses.data.repository.RoomIncomeRepository
import com.evgurov.expenses.data.repository.RoomLoanRepository
import com.evgurov.expenses.data.repository.RoomTransactionRepository
import com.evgurov.expenses.domain.repository.AccountRepository
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpensesDatabase =
        Room.databaseBuilder(context, ExpensesDatabase::class.java, ExpensesDatabase.NAME)
            .addCallback(ExpensesDatabase.Seed)
            // Версия 1 не публиковалась: при обновлении тестовой сборки базу пересоздаём.
            .fallbackToDestructiveMigrationFrom(1)
            .addMigrations(ExpensesDatabase.MIGRATION_2_3, ExpensesDatabase.MIGRATION_3_4, ExpensesDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun provideAccountDao(db: ExpensesDatabase) = db.accountDao()

    @Provides
    fun provideCategoryDao(db: ExpensesDatabase) = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: ExpensesDatabase) = db.transactionDao()

    @Provides
    fun provideLoanDao(db: ExpensesDatabase) = db.loanDao()

    @Provides
    fun provideIncomeSourceDao(db: ExpensesDatabase) = db.incomeSourceDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    abstract fun bindLoanRepository(impl: RoomLoanRepository): LoanRepository

    @Binds
    abstract fun bindIncomeRepository(impl: RoomIncomeRepository): IncomeRepository
}
