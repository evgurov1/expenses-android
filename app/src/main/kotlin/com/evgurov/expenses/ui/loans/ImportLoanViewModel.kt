package com.evgurov.expenses.ui.loans

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.domain.importing.LoanDocumentParser
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

    fun importPdf(uri: Uri) = run("Читаю PDF…", LoanSource.CONTRACT) { extractor.fromPdf(uri) }

    fun importImage(uri: Uri) = run("Распознаю текст на изображении… Это может занять до минуты.", LoanSource.PHOTO) {
        extractor.fromImage(uri)
    }

    fun reset() {
        _state.value = ImportState.Idle
    }

    private fun run(message: String, source: LoanSource, extract: suspend () -> String) {
        if (_state.value is ImportState.Working) return
        _state.value = ImportState.Working(message)
        viewModelScope.launch {
            val result = runCatching { LoanDocumentParser.parse(extract()) }
            _state.value = result.fold(
                onSuccess = { parsed ->
                    if (parsed.isEmpty) {
                        ImportState.Failed(
                            "Не нашёл в документе данных кредита. Попробуйте график платежей или договор в PDF, " +
                                "либо более чёткое фото.",
                        )
                    } else {
                        drafts.put(ImportDraft(parsed, source))
                        ImportState.Done
                    }
                },
                onFailure = { ImportState.Failed("Не удалось прочитать файл: ${it.message ?: it.javaClass.simpleName}") },
            )
        }
    }
}
