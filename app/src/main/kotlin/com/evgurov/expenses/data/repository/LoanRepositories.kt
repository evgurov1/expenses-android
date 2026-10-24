package com.evgurov.expenses.data.repository

import com.evgurov.expenses.data.db.IncomeSourceDao
import com.evgurov.expenses.data.db.LoanDao
import com.evgurov.expenses.data.db.toDomain
import com.evgurov.expenses.data.db.toEntity
import com.evgurov.expenses.domain.model.IncomeSource
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomLoanRepository @Inject constructor(private val dao: LoanDao) : LoanRepository {
    override fun observeAll(): Flow<List<Loan>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Loan?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun upsert(loan: Loan): Long = dao.upsert(loan.toEntity())
    override suspend fun delete(loan: Loan) = dao.delete(loan.toEntity())
}

class RoomIncomeRepository @Inject constructor(private val dao: IncomeSourceDao) : IncomeRepository {
    override fun observeAll(): Flow<List<IncomeSource>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(source: IncomeSource): Long = dao.upsert(source.toEntity())
    override suspend fun delete(source: IncomeSource) = dao.delete(source.toEntity())
}
