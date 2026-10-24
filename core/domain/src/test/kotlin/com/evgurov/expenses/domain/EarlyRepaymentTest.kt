package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.usecase.EarlyMode
import com.evgurov.expenses.domain.usecase.EarlyRepayment
import com.evgurov.expenses.domain.usecase.LoanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EarlyRepaymentTest {
    private val loan = Loan(
        id = 1, type = LoanType.CONSUMER, name = "Кредит", principal = 200_000_00, annualRate = 40.7,
        termMonths = 60, issueDate = LocalDate.of(2026, 8, 2), monthlyPayment = 7_844_00,
    )
    private val date = LocalDate.of(2026, 10, 24)

    @Test
    fun `reduce term keeps payment and closes earlier`() {
        val before = LoanMath.balanceOn(loan, date)
        val result = EarlyRepayment.apply(loan, date, 50_000_00, EarlyMode.REDUCE_TERM)
        assertEquals(before - 50_000_00, result.balanceAfter)
        assertEquals(result.balanceAfter, LoanMath.balanceOn(result.loan, date))
        assertEquals(7_844_00L, result.newPayment)
        assertTrue(result.newEndDate!!.isBefore(LoanMath.endDate(loan)))
        assertTrue(result.savedInterest > 0)
        // Досрочка не считается обязательным платежом в ПДН.
        assertEquals(7_844_00L, LoanMath.paymentInMonth(result.loan, YearMonth.of(2026, 11)))
        assertEquals(1, result.loan.bankSchedule.count { it.early })
        assertEquals(0L, result.loan.bankSchedule.last().balanceAfter)
    }

    @Test
    fun `reduce payment keeps the end date`() {
        val result = EarlyRepayment.apply(loan, date, 50_000_00, EarlyMode.REDUCE_PAYMENT)
        assertEquals(LoanMath.endDate(loan), result.newEndDate)
        assertTrue(result.newPayment < 7_844_00)
        assertEquals(result.newPayment, LoanMath.regularPayment(result.loan))
        assertEquals(0L, result.loan.bankSchedule.last().balanceAfter)
    }
}
