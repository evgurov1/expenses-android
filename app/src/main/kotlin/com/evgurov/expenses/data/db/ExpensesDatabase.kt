package com.evgurov.expenses.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ExpensesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    /** Заполняет новую базу стартовыми категориями и счетом «Наличные». */
    object Seed : Callback() {
        private val expenseCategories = listOf(
            "Продукты", "Кафе и рестораны", "Транспорт", "Жильё и ЖКХ", "Связь и интернет",
            "Здоровье", "Одежда", "Развлечения", "Платежи по кредитам", "Прочее",
        )
        private val incomeCategories = listOf("Зарплата", "Подработка", "Проценты и кешбэк", "Прочие доходы")

        override fun onCreate(db: SupportSQLiteDatabase) {
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
