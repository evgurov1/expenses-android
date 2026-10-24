package com.evgurov.expenses.domain.model

import java.time.LocalDate

/** Все денежные суммы хранятся в копейках, чтобы не было ошибок округления. */
typealias Kopecks = Long

enum class TransactionKind { INCOME, EXPENSE }

enum class AccountType { CARD, CASH, DEPOSIT, OTHER }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String = "RUB",
    val initialBalance: Kopecks = 0,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val kind: TransactionKind,
    val parentId: Long? = null,
)

/** Сумма операции всегда положительная, направление задаёт [kind]. */
data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val categoryId: Long,
    val amount: Kopecks,
    val kind: TransactionKind,
    val date: LocalDate,
    val note: String = "",
) {
    init {
        require(amount > 0) { "Сумма операции должна быть больше нуля" }
    }

    val signedAmount: Kopecks get() = if (kind == TransactionKind.INCOME) amount else -amount
}
