package com.evgurov.expenses.assistant

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AssistantAvailability { CHECKING, READY, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

sealed interface ModelDownload {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownload
    data object Done : ModelDownload
    data class Failed(val message: String) : ModelDownload
}

/**
 * Встроенная в телефон модель Gemini Nano (через системный сервис AICore).
 * Запросы обрабатываются на устройстве и никуда не отправляются.
 */
@Singleton
class LocalAssistant @Inject constructor() {
    private val model: GenerativeModel by lazy { Generation.getClient() }
    private var totalBytes = 0L

    suspend fun availability(): AssistantAvailability = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> AssistantAvailability.READY
            FeatureStatus.DOWNLOADABLE -> AssistantAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> AssistantAvailability.DOWNLOADING
            else -> AssistantAvailability.UNAVAILABLE
        }
    }.getOrDefault(AssistantAvailability.UNAVAILABLE)

    /** Модель скачивается системой один раз; дальше работает без интернета. */
    fun download(): Flow<ModelDownload> = model.download().map { status ->
        when (status) {
            is DownloadStatus.DownloadStarted -> {
                totalBytes = status.bytesToDownload
                ModelDownload.Progress(0, totalBytes)
            }
            is DownloadStatus.DownloadProgress -> ModelDownload.Progress(status.totalBytesDownloaded, totalBytes)
            is DownloadStatus.DownloadCompleted -> ModelDownload.Done
            is DownloadStatus.DownloadFailed -> ModelDownload.Failed(status.e.message ?: "Не удалось скачать модель")
            else -> ModelDownload.Progress(0, totalBytes)
        }
    }

    /** Ответ по частям, по мере генерации. Каждый элемент — новый кусок текста. */
    fun answer(prompt: String): Flow<String> {
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 320
        }
        return model.generateContentStream(request).map { response ->
            response.candidates.firstOrNull()?.text.orEmpty()
        }
    }
}
