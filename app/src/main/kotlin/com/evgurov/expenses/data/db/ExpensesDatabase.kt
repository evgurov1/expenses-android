package com.evgurov.expenses.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        IncomeSourceEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ExpensesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun incomeSourceDao(): IncomeSourceDao

    /** Заполняет новую базу стартовыми категориями и счетом «Наличные». */
    object Seed : Callback() {
        private val expenseCategories = listOf(
            "Продукты", "Кафе и рестораны", "Транспорт", "Жильё и ЖКХ", "Связь и интернет",
            "Здоровье", "Одежда", "Развлечения", "Платежи по кредитам", "Прочее",
        )
        private val incomeCategories = listOf("Зарплата", "Подработка", "Проценты и кешбэк", "Прочие доходы")

        /**
         * Заполняем при открытии, если база пустая: так стартовые данные появляются
         * и в новой базе, и после пересоздания при обновлении с версии 1.
         */
        override fun onOpen(db: SupportSQLiteDatabase) {
            if (isEmpty(db, "categories") && isEmpty(db, "accounts")) seed(db)
        }

        private fun isEmpty(db: SupportSQLiteDatabase, table: String): Boolean =
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst() && it.getLong(0) == 0L }

        private fun seed(db: SupportSQLiteDatabase) {
            expenseCategories.forEach { insertCategory(db, it, "EXPENSE") }
            incomeCategories.forEach { insertCategory(db, it, "INCOME") }
            db.execSQL(
                "INSERT INTO accounts (name, type, currency, initialBalance) VALUES (?, 'CASH', 'RUB', 0)",
                arrayOf("Наличные"),
            )
        }

        private fun insertCategory(db: SupportSQLiteDatabase, name: String, kind: String) {
            db.execSQL("INSERT INTO categories (name, kind, parentId) VALUES (?, ?, NULL)", arrayOf(name, kind))
        }
    }

    companion object {
        const val NAME = "expenses.db"
    }
}
