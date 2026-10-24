package com.evgurov.expenses.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        IncomeSourceEntity::class,
        LoanPaymentEntity::class,
        MerchantRuleEntity::class,
    ],
    version = 8,
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

        /** v8: остаток счёта на дату — из уведомления банка, выписки или введённый вручную. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `knownBalance` INTEGER")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `knownBalanceDate` INTEGER")
            }
        }

        /** v7: переводы между своими счетами из выписки — не траты и не доходы, убираем. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM `transactions` WHERE `externalId` LIKE 'stmt:%' AND " +
                        "(`note` LIKE 'Внутренний перевод на договор%' OR `note` LIKE 'Перевод с договора%')",
                )
            }
        }

        /**
         * v6: убираем операции из выписок и справок Т-Банка. Справка задваивала траты, а отличить её
         * операции от операций из выписки нельзя, поэтому выписку пользователь загружает заново.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `transactions` WHERE `externalId` LIKE 'stmt:%'")
            }
        }

        /** v5: операции из уведомлений банков и категории, выбранные пользователем для магазинов. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `externalId` TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_externalId` ON `transactions` (`externalId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `merchant_rules` (`merchant` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`merchant`))",
                )
            }
        }

        /** v4: досрочные платежи пользователя в графике. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `loan_payments` ADD COLUMN `early` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: график платежей из документа банка. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `loanId` INTEGER NOT NULL, " +
                        "`number` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `payment` INTEGER NOT NULL, " +
                        "`principalPart` INTEGER NOT NULL, `interestPart` INTEGER NOT NULL, " +
                        "`balanceAfter` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
            }
        }
    }
}
