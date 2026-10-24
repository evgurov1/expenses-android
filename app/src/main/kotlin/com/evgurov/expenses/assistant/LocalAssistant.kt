package com.evgurov.expenses.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

enum class AssistantAvailability { CHECKING, DOWNLOADABLE, DOWNLOADING, LOADING, READY, UNAVAILABLE }

sealed interface ModelDownload {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownload
    data object Done : ModelDownload
    data class Failed(val message: String) : ModelDownload
}

/**
 * Модель Qwen3 4B, которая работает в телефоне через llama.cpp.
 * Из сети только один раз скачивается файл модели; вопросы и данные о кредитах никуда не отправляются.
 */
@Singleton
class LocalAssistant @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)

    // llama.cpp не любит вызовы из разных потоков: все обращения — через один поток.
    private val llmThread = Executors.newSingleThreadExecutor { Thread(it, "llm").apply { priority = Thread.MAX_PRIORITY } }
        .asCoroutineDispatcher()
    private val loadLock = Mutex()
    private var handle = 0L

    private val modelFile: File get() = File(context.getExternalFilesDir("models"), MODEL_FILE)

    val isLoaded: Boolean get() = handle != 0L

    fun availability(): AssistantAvailability = when {
        isLoaded -> AssistantAvailability.READY
        modelFile.length() == MODEL_SIZE -> AssistantAvailability.LOADING
        runningDownload() != null -> AssistantAvailability.DOWNLOADING
        else -> AssistantAvailability.DOWNLOADABLE
    }

    /** Скачивает модель через системный загрузчик (переживает закрытие приложения) и сообщает прогресс. */
    fun download(): Flow<ModelDownload> = flow {
        val id = runningDownload() ?: run {
            modelFile.parentFile?.mkdirs()
            modelFile.delete()
            val request = DownloadManager.Request(Uri.parse(MODEL_URL))
                .setTitle("Модель помощника Qwen3 4B")
                .setDescription("Скачивается один раз, дальше работает без интернета")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(modelFile))
            downloads.enqueue(request).also { prefs.edit().putLong(KEY_DOWNLOAD, it).apply() }
        }
        while (true) {
            val (status, done, total) = query(id) ?: run {
                emit(ModelDownload.Failed("Загрузка отменена"))
                return@flow
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove(KEY_DOWNLOAD).apply()
                    if (modelFile.length() == MODEL_SIZE) emit(ModelDownload.Done)
                    else emit(ModelDownload.Failed("Файл модели скачался не полностью, попробуйте ещё раз"))
                    return@flow
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove(KEY_DOWNLOAD).apply()
                    emit(ModelDownload.Failed("Не удалось скачать модель. Проверьте интернет и свободное место (нужно 2,5 ГБ)."))
                    return@flow
                }
                else -> emit(ModelDownload.Progress(done, if (total > 0) total else MODEL_SIZE))
            }
            delay(700)
        }
    }.flowOn(Dispatchers.IO)

    /** Загружает модель в память. Первый раз занимает 5–20 секунд. */
    suspend fun load(): Boolean = loadLock.withLock {
        if (isLoaded) return true
        if (modelFile.length() != MODEL_SIZE) return false
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        handle = withContext(llmThread) { LlamaNative.load(modelFile.absolutePath, CONTEXT_SIZE, threads) }
        isLoaded
    }

    /** Ответ модели по кускам текста. maxTokens = 0 — только посчитать запрос заранее, чтобы следующий ответ был быстрее. */
    fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0.3f): Flow<String> = callbackFlow {
        val job = launch(llmThread) {
            val code = LlamaNative.generate(handle, prompt.toByteArray(), maxTokens, temperature) { bytes ->
                trySendBlocking(String(bytes, Charsets.UTF_8))
                isActive
            }
            if (code == -2) close(PromptTooLong()) else if (code < 0) close(IllegalStateException("Модель не смогла ответить ($code)")) else close()
        }
        awaitClose { job.cancel() }
    }

    class PromptTooLong : IllegalStateException("Слишком длинный разговор")

    private fun runningDownload(): Long? {
        val id = prefs.getLong(KEY_DOWNLOAD, -1).takeIf { it >= 0 } ?: return null
        val status = query(id)?.first ?: return null
        return id.takeIf { status != DownloadManager.STATUS_FAILED && status != DownloadManager.STATUS_SUCCESSFUL }
    }

    private fun query(id: Long): Triple<Int, Long, Long>? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            Triple(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    private companion object {
        const val MODEL_FILE = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
        const val MODEL_URL = "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/$MODEL_FILE"
        const val MODEL_SIZE = 2_497_281_120L
        const val CONTEXT_SIZE = 8192
        const val KEY_DOWNLOAD = "model_download_id"
    }
}
