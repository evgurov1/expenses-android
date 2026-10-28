package com.evgurov.expenses.ui.loans

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.importing.LoanDocumentParser
import com.evgurov.expenses.domain.importing.mergeLoans
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.importing.ImportDraft
import com.evgurov.expenses.importing.ImportDraftHolder
import com.evgurov.expenses.importing.LoanTextExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val message: String) : ImportState
    data class Failed(val message: String) : ImportState
    data object Done : ImportState
}

@HiltViewModel
class ImportLoanViewModel @Inject constructor(
    private val extractor: LoanTextExtractor,
    private val drafts: ImportDraftHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Разбирает все выбранные файлы (PDF и картинки вперемешку) и собирает из них один кредит. */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value is ImportState.Working) return
        viewModelScope.launch {
            var anyPdf = false
            val errors = mutableListOf<String>()
            val parsed = uris.mapIndexedNotNull { index, uri ->
                val counter = if (uris.size > 1) " (${index + 1} из ${uris.size})" else ""
                runCatching {
                    val pdf = extractor.isPdf(uri)
                    anyPdf = anyPdf || pdf
                    _state.value = ImportState.Working(
                        if (pdf) "Читаю PDF$counter…" else "Распознаю текст на изображении$counter… Это может занять до минуты.",
                    )
                    LoanDocumentParser.parse(extractor.fromAny(uri))
                }.onFailure { errors += it.message ?: it.javaClass.simpleName }.getOrNull()
            }
            val loan = parsed.mergeLoans()
            _state.value = when {
                !loan.isEmpty -> {
                    drafts.put(ImportDraft(loan, if (anyPdf) LoanSource.CONTRACT else LoanSource.PHOTO))
                    ImportState.Done
                }
                errors.isNotEmpty() && parsed.isEmpty() -> ImportState.Failed("Не удалось прочитать файлы: ${errors.first()}")
                else -> ImportState.Failed(
                    "Не нашёл данных кредита. Добавьте график платежей или договор в PDF, скриншот кредита из приложения банка " +
                        "или более чёткое фото.",
                )
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }
}
