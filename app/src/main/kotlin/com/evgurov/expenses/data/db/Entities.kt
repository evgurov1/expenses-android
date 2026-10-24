package com.evgurov.expenses.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.LocalDate

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalance: Long,
    val knownBalance: Long? = null,
    val knownBalanceDate: LocalDate? = null,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: TransactionKind,
    val parentId: Long?,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("date"), Index(value = ["externalId"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long,
    val amount: Long,
    val kind: TransactionKind,
    val date: LocalDate,
    val note: String,
    val externalId: String? = null,
)

/** Категория, которую пользователь выбрал для магазина: следующие покупки там получат её же. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchant: String,
    val categoryId: Long,
)

fun AccountEntity.toDomain() = Account(id, name, type, currency, initialBalance, knownBalance, knownBalanceDate)
fun Account.toEntity() = AccountEntity(id, name, type, currency, initialBalance, knownBalance, knownBalanceDate)

fun CategoryEntity.toDomain() = Category(id, name, kind, parentId)
fun Category.toEntity() = CategoryEntity(id, name, kind, parentId)

fun TransactionEntity.toDomain() = Transaction(id, accountId, categoryId, amount, kind, date, note, externalId)
fun Transaction.toEntity() = TransactionEntity(id, accountId, categoryId, amount, kind, date, note, externalId)
