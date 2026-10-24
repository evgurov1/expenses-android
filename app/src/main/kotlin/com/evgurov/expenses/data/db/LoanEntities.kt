package com.evgurov.expenses.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.model.ScheduleItem
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

/** Строка графика платежей из документа банка. */
@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("loanId")],
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val number: Int,
    val dueDate: LocalDate,
    val payment: Long,
    val principalPart: Long,
    val interestPart: Long,
    val balanceAfter: Long,
    @ColumnInfo(defaultValue = "0") val early: Boolean = false,
)

data class LoanWithPayments(
    @Embedded val loan: LoanEntity,
    @Relation(parentColumn = "id", entityColumn = "loanId")
    val payments: List<LoanPaymentEntity>,
)

@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyAmount: Long,
)

fun LoanWithPayments.toDomain() = Loan(
    loan.id, loan.type, loan.name, loan.bank, loan.principal, loan.annualRate, loan.termMonths, loan.issueDate,
    loan.paymentType, loan.monthlyPayment, loan.creditLimit, loan.currentBalance, loan.source, loan.note,
    bankSchedule = payments.sortedBy { it.number }.map {
        ScheduleItem(it.number, it.dueDate, it.payment, it.principalPart, it.interestPart, it.balanceAfter, it.early)
    },
)

fun Loan.toPaymentEntities(loanId: Long) = bankSchedule.map {
    LoanPaymentEntity(
        loanId = loanId,
        number = it.number,
        dueDate = it.dueDate,
        payment = it.payment,
        principalPart = it.principalPart,
        interestPart = it.interestPart,
        balanceAfter = it.balanceAfter,
        early = it.early,
    )
}

fun Loan.toEntity() = LoanEntity(
    id, type, name, bank, principal, annualRate, termMonths, issueDate,
    paymentType, monthlyPayment, creditLimit, currentBalance, source, note,
)

fun IncomeSourceEntity.toDomain() = IncomeSource(id, name, monthlyAmount)
fun IncomeSource.toEntity() = IncomeSourceEntity(id, name, monthlyAmount)
