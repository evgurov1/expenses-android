package com.evgurov.expenses.data.repository

import com.evgurov.expenses.data.db.IncomeSourceDao
import com.evgurov.expenses.data.db.LoanDao
import com.evgurov.expenses.data.db.toDomain
import com.evgurov.expenses.data.db.toEntity
import com.evgurov.expenses.data.db.toPaymentEntities
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class RoomLoanRepository @Inject constructor(private val dao: LoanDao) : LoanRepository {
    override fun observeAll(): Flow<List<Loan>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Loan?> = dao.observe(id).map { it?.toDomain() }
    override fun observeClosed(): Flow<List<Loan>> = dao.observeClosed().map { list -> list.map { it.toDomain() } }
    override suspend fun setClosed(id: Long, date: LocalDate?) = dao.setClosed(id, date)

    // Правка кредита не должна возвращать погашенный кредит в действующие.
    override suspend fun upsert(loan: Loan): Long {
        val closed = loan.closedOn ?: if (loan.id != 0L) dao.closedOn(loan.id) else null
        return dao.save(loan.copy(closedOn = closed).toEntity(), loan.toPaymentEntities(loan.id))
    }
    override suspend fun delete(loan: Loan) = dao.delete(loan.toEntity())
}

class RoomIncomeRepository @Inject constructor(private val dao: IncomeSourceDao) : IncomeRepository {
    override fun observeAll(): Flow<List<IncomeSource>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(source: IncomeSource): Long = dao.upsert(source.toEntity())
    override suspend fun delete(source: IncomeSource) = dao.delete(source.toEntity())
}
