package com.evgurov.expenses.importing

import com.evgurov.expenses.domain.importing.ParsedLoan
import com.evgurov.expenses.domain.model.LoanSource
import javax.inject.Inject
import javax.inject.Singleton

data class ImportDraft(val parsed: ParsedLoan, val source: LoanSource)

/** Передаёт распознанный кредит с экрана импорта на форму проверки. */
@Singleton
class ImportDraftHolder @Inject constructor() {
    private var draft: ImportDraft? = null

    fun put(value: ImportDraft) {
        draft = value
    }

    fun take(): ImportDraft? = draft.also { draft = null }
}
