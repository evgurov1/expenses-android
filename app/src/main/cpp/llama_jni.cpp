// Мост между Kotlin и llama.cpp: загрузка модели и генерация с повторным использованием KV-кэша.
#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "ExpensesLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    // Токены, которые сейчас лежат в KV-кэше: общий префикс следующего запроса не пересчитываем.
    std::vector<llama_token> cached;
    int n_batch = 512;
};

std::vector<llama_token> tokenize(const Engine & e, const std::string & text) {
    int n = -llama_tokenize(e.vocab, text.data(), (int32_t) text.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(std::max(n, 0));
    if (n > 0) {
        llama_tokenize(e.vocab, text.data(), (int32_t) text.size(), tokens.data(), n, true, true);
    }
    return tokens;
}

std::string piece(const Engine & e, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(e.vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(-n, '\0');
        llama_token_to_piece(e.vocab, token, big.data(), -n, 0, false);
        return big;
    }
    return std::string(buf, n);
}

// Длина начала строки, которое состоит из целых UTF-8 символов: недописанный символ ждёт следующего токена.
size_t complete_utf8(const std::string & s) {
    size_t i = s.size();
    size_t back = 0;
    while (i > 0 && back < 4) {
        unsigned char c = s[i - 1];
        if ((c & 0xC0) != 0x80) {
            size_t need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : 4;
            return (back + 1 >= need) ? s.size() : i - 1;
        }
        --i;
        ++back;
    }
    return s.size();
}

bool decode(Engine & e, const std::vector<llama_token> & tokens, size_t from) {
    llama_batch batch = llama_batch_init(e.n_batch, 0, 1);
    for (size_t i = from; i < tokens.size(); i += e.n_batch) {
        size_t n = std::min((size_t) e.n_batch, tokens.size() - i);
        batch.n_tokens = (int32_t) n;
        for (size_t j = 0; j < n; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = (llama_pos) (i + j);
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (i + j == tokens.size() - 1);
        }
        if (llama_decode(e.ctx, batch) != 0) {
            llama_batch_free(batch);
            return false;
        }
    }
    llama_batch_free(batch);
    return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_evgurov_expenses_assistant_LlamaNative_load(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    llama_backend_init();
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) {
        LOGE("model load failed");
        return 0;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_ctx;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    // Кэш в 8 бит вдвое экономит память: на телефоне свободно 3–4 ГБ, а модель занимает 2,5.
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    cparams.type_k = GGML_TYPE_Q8_0;
    cparams.type_v = GGML_TYPE_Q8_0;
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("context init failed");
        llama_model_free(model);
        return 0;
    }
    auto * e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    LOGI("loaded: %s", llama_print_system_info());
    return reinterpret_cast<jlong>(e);
}

extern "C" JNIEXPORT void JNICALL
Java_com_evgurov_expenses_assistant_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e) return;
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

/**
 * Генерирует ответ на [prompt] (UTF-8). Куски текста отдаёт в callback.onText(byte[]);
 * если callback вернул false — генерация останавливается. max_tokens = 0 — только прогреть кэш.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_evgurov_expenses_assistant_LlamaNative_generate(
        JNIEnv * env, jobject, jlong handle, jbyteArray jprompt, jint max_tokens, jfloat temperature, jobject callback) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e) return -1;

    jsize len = env->GetArrayLength(jprompt);
    std::string prompt(len, '\0');
    env->GetByteArrayRegion(jprompt, 0, len, reinterpret_cast<jbyte *>(prompt.data()));

    std::vector<llama_token> tokens = tokenize(*e, prompt);
    const int n_ctx = (int) llama_n_ctx(e->ctx);
    if (tokens.empty() || (int) tokens.size() + max_tokens >= n_ctx) {
        LOGE("prompt too long: %zu tokens", tokens.size());
        return -2;
    }

    // Общий префикс с тем, что уже посчитано, — его не пересчитываем.
    size_t common = 0;
    while (common < e->cached.size() && common < tokens.size() && e->cached[common] == tokens[common]) ++common;
    if (common == tokens.size()) --common;  // нужен хотя бы один токен, чтобы получить логиты
    llama_memory_seq_rm(llama_get_memory(e->ctx), 0, (llama_pos) common, -1);
    e->cached.resize(common);
    const int64_t t0 = llama_time_us();
    if (!decode(*e, tokens, common)) {
        llama_memory_clear(llama_get_memory(e->ctx), true);
        e->cached.clear();
        LOGE("prompt decode failed");
        return -3;
    }
    e->cached = tokens;
    const int64_t t1 = llama_time_us();
    LOGI("prompt: %zu tokens, %zu reused, %zu computed in %.1f s",
         tokens.size(), common, tokens.size() - common, (t1 - t0) / 1e6);

    jclass cls = env->GetObjectClass(callback);
    jmethodID on_text = env->GetMethodID(cls, "onText", "([B)Z");

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(20));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.8f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string pending;
    int produced = 0;
    llama_batch batch = llama_batch_init(1, 0, 1);
    while (produced < max_tokens) {
        llama_token token = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, token)) break;
        pending += piece(*e, token);
        ++produced;

        size_t ready = complete_utf8(pending);
        if (ready > 0) {
            jbyteArray chunk = env->NewByteArray((jsize) ready);
            env->SetByteArrayRegion(chunk, 0, (jsize) ready, reinterpret_cast<const jbyte *>(pending.data()));
            jboolean go_on = env->CallBooleanMethod(callback, on_text, chunk);
            env->DeleteLocalRef(chunk);
            pending.erase(0, ready);
            if (!go_on || env->ExceptionCheck()) break;
        }

        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = (llama_pos) e->cached.size();
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        if (llama_decode(e->ctx, batch) != 0) {
            LOGE("token decode failed");
            break;
        }
        e->cached.push_back(token);
    }
    llama_batch_free(batch);
    llama_sampler_free(smpl);
    if (produced > 0) {
        const double secs = (llama_time_us() - t1) / 1e6;
        LOGI("generated %d tokens in %.1f s (%.1f tok/s)", produced, secs, produced / secs);
    }
    return produced;
}

/** Сохраняет посчитанный запрос (KV-кэш) в файл: после перезапуска не нужно считать его заново. */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_evgurov_expenses_assistant_LlamaNative_saveState(JNIEnv * env, jobject, jlong handle, jstring jpath) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e || e->cached.empty()) return false;
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    size_t n = llama_state_seq_save_file(e->ctx, path, 0, e->cached.data(), e->cached.size());
    env->ReleaseStringUTFChars(jpath, path);
    LOGI("state saved: %zu tokens, %zu bytes", e->cached.size(), n);
    return n > 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_evgurov_expenses_assistant_LlamaNative_loadState(JNIEnv * env, jobject, jlong handle, jstring jpath) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e) return false;
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    std::vector<llama_token> tokens(llama_n_ctx(e->ctx));
    size_t count = 0;
    const int64_t t0 = llama_time_us();
    size_t n = llama_state_seq_load_file(e->ctx, path, 0, tokens.data(), tokens.size(), &count);
    env->ReleaseStringUTFChars(jpath, path);
    if (n == 0) {
        llama_memory_clear(llama_get_memory(e->ctx), true);
        e->cached.clear();
        return false;
    }
    tokens.resize(count);
    e->cached = tokens;
    LOGI("state loaded: %zu tokens in %.1f s", count, (llama_time_us() - t0) / 1e6);
    return true;
}
