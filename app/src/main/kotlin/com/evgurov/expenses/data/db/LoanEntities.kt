package com.evgurov.expenses.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.PaymentType
import java.time.LocalDate

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LoanType,
    val name: String,
    val bank: String,
    val principal: Long,
    val annualRate: Double,
    val termMonths: Int,
    val issueDate: LocalDate,
    val paymentType: PaymentType,
    val monthlyPayment: Long?,
    val creditLimit: Long?,
    val currentBalance: Long?,
    val source: LoanSource,
    val note: String,
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyAmount: Long,
)

fun LoanEntity.toDomain() = Loan(
    id, type, name, bank, principal, annualRate, termMonths, issueDate,
    paymentType, monthlyPayment, creditLimit, currentBalance, source, note,
)

fun Loan.toEntity() = LoanEntity(
    id, type, name, bank, principal, annualRate, termMonths, issueDate,
    paymentType, monthlyPayment, creditLimit, currentBalance, source, note,
)

fun IncomeSourceEntity.toDomain() = IncomeSource(id, name, monthlyAmount)
fun IncomeSource.toEntity() = IncomeSourceEntity(id, name, monthlyAmount)
