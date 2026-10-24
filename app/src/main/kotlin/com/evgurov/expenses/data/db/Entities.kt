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
    indices = [Index("accountId"), Index("categoryId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long,
    val amount: Long,
    val kind: TransactionKind,
    val date: LocalDate,
    val note: String,
)

fun AccountEntity.toDomain() = Account(id, name, type, currency, initialBalance)
fun Account.toEntity() = AccountEntity(id, name, type, currency, initialBalance)

fun CategoryEntity.toDomain() = Category(id, name, kind, parentId)
fun Category.toEntity() = CategoryEntity(id, name, kind, parentId)

fun TransactionEntity.toDomain() = Transaction(id, accountId, categoryId, amount, kind, date, note)
fun Transaction.toEntity() = TransactionEntity(id, accountId, categoryId, amount, kind, date, note)
