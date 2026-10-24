package com.evgurov.expenses.assistant

/** Кусок сгенерированного текста в UTF-8. Вернуть false — остановить генерацию. */
fun interface TextCallback {
    fun onText(bytes: ByteArray): Boolean
}

/** Обёртка над llama.cpp (app/src/main/cpp/llama_jni.cpp). */
object LlamaNative {
    init {
        System.loadLibrary("expenses_llm")
    }

    /** Возвращает дескриптор модели или 0, если загрузить не удалось. */
    external fun load(path: String, nCtx: Int, nThreads: Int): Long

    /** Число сгенерированных токенов или отрицательный код ошибки (-2 — запрос не помещается в контекст). */
    external fun generate(handle: Long, prompt: ByteArray, maxTokens: Int, temperature: Float, callback: TextCallback): Int

    external fun free(handle: Long)
}
