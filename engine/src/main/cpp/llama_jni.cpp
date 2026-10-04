// LFM Local — JNI bridge to llama.cpp
// Design: Kotlin formats the LFM2 chat prompt (<|im_start|>...), this file
// only does completion. Keeps us independent of common/chat jinja changes.

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <chrono>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "lfm_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct NativeContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
    std::atomic<bool> abort{false};
    std::mutex gen_mutex; // only one generation at a time
    int n_ctx = 2048;
    int actual_gpu_layers = 0;
    std::vector<llama_token> prev_tokens;
};

static NativeContext* as_ctx(jlong h) {
    return reinterpret_cast<NativeContext*>(h);
}

static inline void batch_add_token(llama_batch& batch, llama_token tok, llama_pos pos, bool logits) {
    batch.token[batch.n_tokens] = tok;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = 0;
    batch.logits[batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeInit(JNIEnv* env, jclass) {
    static bool initialized = false;
    if (initialized) return;
    initialized = true;
    setenv("OMP_WAIT_POLICY", "PASSIVE", 1);
    llama_backend_init();
    LOGI("llama backend init (once)");
}

JNIEXPORT jlong JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeLoadModel(
        JNIEnv* env, jclass,
        jstring jpath, jint n_ctx, jint n_threads, jint n_gpu_layers) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    std::string model_path(path ? path : "");
    if (path) env->ReleaseStringUTFChars(jpath, path);

    if (model_path.empty()) return 0;

    auto* nc = new NativeContext();
    nc->n_ctx = n_ctx > 0 ? n_ctx : 2048;

    llama_model_params mparams = llama_model_default_params();
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;
    mparams.n_gpu_layers = n_gpu_layers > 0 ? n_gpu_layers : 0;

    // Attempt loading with GPU layers; if GPU allocation fails, automatically offload to CPU
    nc->model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (!nc->model && mparams.n_gpu_layers > 0) {
        LOGI("GPU model load failed (%d layers); automatically falling back to CPU", mparams.n_gpu_layers);
        mparams.n_gpu_layers = 0;
        nc->model = llama_model_load_from_file(model_path.c_str(), mparams);
    }
    if (!nc->model) {
        LOGE("model load failed: %s", model_path.c_str());
        delete nc;
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) nc->n_ctx;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads > 0 ? n_threads : 4;
    cparams.n_threads_batch = cparams.n_threads;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;  // massive speedup on ARM NEON
    cparams.type_k = GGML_TYPE_Q8_0;  // quantized KV cache = less memory + faster attention
    cparams.type_v = GGML_TYPE_Q8_0;
    cparams.abort_callback = [](void* data) -> bool {
        auto* ctx_ptr = static_cast<NativeContext*>(data);
        return ctx_ptr && ctx_ptr->abort.load();
    };
    cparams.abort_callback_data = nc;

    nc->ctx = llama_init_from_model(nc->model, cparams);
    if (!nc->ctx && mparams.n_gpu_layers > 0) {
        LOGI("GPU context init failed; automatically falling back to CPU...");
        llama_model_free(nc->model);
        mparams.n_gpu_layers = 0;
        nc->model = llama_model_load_from_file(model_path.c_str(), mparams);
        if (nc->model) {
            nc->ctx = llama_init_from_model(nc->model, cparams);
        }
    }
    if (!nc->ctx) {
        LOGE("context init failed");
        if (nc->model) llama_model_free(nc->model);
        delete nc;
        return 0;
    }

    bool gpu_supported = llama_supports_gpu_offload();
    nc->actual_gpu_layers = (gpu_supported && mparams.n_gpu_layers > 0) ? mparams.n_gpu_layers : 0;
    LOGI("model loaded: %s ctx=%d threads=%d gpu_layers=%d (actual=%d, gpu_supported=%d)",
         model_path.c_str(), nc->n_ctx, cparams.n_threads, n_gpu_layers, nc->actual_gpu_layers, gpu_supported ? 1 : 0);
    return reinterpret_cast<jlong>(nc);
}

JNIEXPORT jint JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeGpuLayers(JNIEnv*, jclass, jlong h) {
    NativeContext* nc = as_ctx(h);
    return nc ? nc->actual_gpu_layers : 0;
}

JNIEXPORT void JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeResetContext(JNIEnv*, jclass, jlong h) {
    NativeContext* nc = as_ctx(h);
    if (!nc) return;
    std::lock_guard<std::mutex> lock(nc->gen_mutex);
    nc->prev_tokens.clear();
    if (nc->ctx) {
        llama_memory_clear(llama_get_memory(nc->ctx), false);
    }
    LOGI("context memory reset");
}

JNIEXPORT void JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeFreeModel(JNIEnv*, jclass, jlong h) {
    NativeContext* nc = as_ctx(h);
    if (!nc) return;
    nc->abort.store(true);
    std::lock_guard<std::mutex> lock(nc->gen_mutex);
    if (nc->sampler) { llama_sampler_free(nc->sampler); nc->sampler = nullptr; }
    if (nc->ctx) { llama_free(nc->ctx); nc->ctx = nullptr; }
    if (nc->model) { llama_model_free(nc->model); nc->model = nullptr; }
    delete nc;
}

JNIEXPORT void JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeStop(JNIEnv*, jclass, jlong h) {
    NativeContext* nc = as_ctx(h);
    if (nc) nc->abort.store(true);
}

JNIEXPORT jint JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeContextSize(JNIEnv*, jclass, jlong h) {
    NativeContext* nc = as_ctx(h);
    if (!nc || !nc->ctx) return 0;
    return (jint) llama_n_ctx(nc->ctx);
}

static size_t valid_utf8_prefix_len(const std::string& str) {
    size_t len = str.size();
    if (len == 0) return 0;
    for (size_t i = 1; i <= 4 && i <= len; ++i) {
        unsigned char c = static_cast<unsigned char>(str[len - i]);
        if ((c & 0x80) == 0) {
            return len;
        } else if ((c & 0xE0) == 0xC0) {
            return (i >= 2) ? len : len - i;
        } else if ((c & 0xF0) == 0xE0) {
            return (i >= 3) ? len : len - i;
        } else if ((c & 0xF8) == 0xF0) {
            return (i >= 4) ? len : len - i;
        }
    }
    return len;
}

// Blocking generation with streaming callback.
// callback: com.lfmlocal.app.inference.TokenCallback { void onToken(String piece); boolean isCancelled(); }
JNIEXPORT jstring JNICALL
Java_com_lfmlocal_app_inference_LlamaBridge_nativeGenerate(
        JNIEnv* env, jclass,
        jlong h,
        jstring jprompt,
        jint max_tokens,
        jfloat temp,
        jfloat top_p,
        jint top_k,
        jfloat repeat_penalty,
        jobject callback) {
    NativeContext* nc = as_ctx(h);
    if (!nc || !nc->ctx || !nc->model) {
        return env->NewStringUTF("[error] model not loaded");
    }
    std::lock_guard<std::mutex> lock(nc->gen_mutex);
    nc->abort.store(false);

    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c ? prompt_c : "");
    if (prompt_c) env->ReleaseStringUTFChars(jprompt, prompt_c);

    jclass cb_cls = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID on_token = (callback && cb_cls)
        ? env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)V") : nullptr;
    jmethodID is_cancelled = (callback && cb_cls)
        ? env->GetMethodID(cb_cls, "isCancelled", "()Z") : nullptr;

    const llama_vocab* vocab = llama_model_get_vocab(nc->model);
    const int n_ctx = (int) llama_n_ctx(nc->ctx);

    // Tokenize prompt
    std::vector<llama_token> prompt_tokens(prompt.size() + 64);
    int n_prompt = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(),
                                  prompt_tokens.data(), (int)prompt_tokens.size(), true, true);
    if (n_prompt < 0) {
        prompt_tokens.resize((size_t)(-n_prompt) + 16);
        n_prompt = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(),
                                  prompt_tokens.data(), (int)prompt_tokens.size(), true, true);
    }
    if (n_prompt <= 0) {
        return env->NewStringUTF("[error] failed to tokenize prompt");
    }
    if (n_prompt >= n_ctx - 64) {
        return env->NewStringUTF("[error] prompt too long for context. Shorten history or raise context size.");
    }

    // (re)build sampler chain per request so temp/top_p apply live
    if (nc->sampler) { llama_sampler_free(nc->sampler); nc->sampler = nullptr; }
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    nc->sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(nc->sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(nc->sampler, llama_sampler_init_top_k((uint32_t)(top_k > 0 ? top_k : 40)));
    llama_sampler_chain_add(nc->sampler, llama_sampler_init_top_p(top_p > 0 ? top_p : 0.95f, 1));
    llama_sampler_chain_add(nc->sampler, llama_sampler_init_temp(temp >= 0 ? temp : 0.7f));
    llama_sampler_chain_add(nc->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    for (int p_idx = 0; p_idx < n_prompt; ++p_idx) {
        llama_sampler_accept(nc->sampler, prompt_tokens[p_idx]);
    }

    // Check common prefix with prev_tokens in warm KV cache
    int n_past = 0;
    while (n_past < (int)nc->prev_tokens.size() &&
           n_past < n_prompt &&
           nc->prev_tokens[n_past] == prompt_tokens[n_past]) {
        n_past++;
    }

    if (n_past < (int)nc->prev_tokens.size()) {
        if (n_past == 0) {
            llama_memory_clear(llama_get_memory(nc->ctx), false);
        } else {
            llama_memory_seq_rm(llama_get_memory(nc->ctx), 0, n_past, -1);
        }
        nc->prev_tokens.resize(n_past);
    }

    LOGI("prompt tokens: %d, warm cached prefix: %d, prefill needed: %d",
         n_prompt, n_past, n_prompt - n_past);

    auto t_prefill_start = std::chrono::steady_clock::now();
    llama_batch batch = llama_batch_init(512, 0, 1);

    // Prefill ONLY new tokens from n_past to n_prompt - 1
    for (int i = n_past; i < n_prompt; ++i) {
        bool is_last = (i == n_prompt - 1);
        batch_add_token(batch, prompt_tokens[i], i, is_last);
        if (batch.n_tokens >= 512 || is_last) {
            if (llama_decode(nc->ctx, batch) != 0) {
                LOGE("llama_decode failed during prompt prefill");
                llama_batch_free(batch);
                return env->NewStringUTF("[error] prompt decode failed (OOM?)");
            }
            batch.n_tokens = 0;
        }
        if (nc->abort.load()) {
            llama_batch_free(batch);
            return env->NewStringUTF("");
        }
    }

    // Sync prompt tokens into context history (must resize to actual token count)
    prompt_tokens.resize(n_prompt);
    nc->prev_tokens = prompt_tokens;

    int prefill_count = n_prompt - n_past;
    if (prefill_count > 0) {
        auto t_prefill_end = std::chrono::steady_clock::now();
        double prefill_ms = std::chrono::duration<double, std::milli>(t_prefill_end - t_prefill_start).count();
        LOGI("prefill: %d tokens in %.1f ms (%.1f tok/s)", prefill_count, prefill_ms,
             prefill_count * 1000.0 / prefill_ms);
    }

    std::string out;
    out.reserve(1024);
    std::string utf8_pending;
    int max_new = max_tokens > 0 ? max_tokens : 512;
    int budget = n_ctx - n_prompt - 8;
    if (budget < 16) budget = 16;
    if (max_new > budget) max_new = budget;

    const llama_token eos = llama_vocab_eos(vocab);

    llama_pos n_cur = n_prompt;
    int generated_count = 0;
    auto t_gen_start = std::chrono::steady_clock::now();
    for (int i = 0; i < max_new; i++) {
        if (nc->abort.load()) break;
        if (callback && is_cancelled && env->CallBooleanMethod(callback, is_cancelled)) break;

        // Sample token from the logits of the last decoded token (index -1)
        llama_token tok = llama_sampler_sample(nc->sampler, nc->ctx, -1);
        llama_sampler_accept(nc->sampler, tok);
        if (tok == eos || llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        std::string piece;
        if (n > 0) piece.assign(buf, (size_t)n);

        // Filter out explicit template stop tags
        if (piece == "<|im_end|>" || piece == "<|endoftext|>" || piece == "<|im_start|>") {
            break;
        }

        out += piece;
        utf8_pending += piece;
        generated_count++;
        nc->prev_tokens.push_back(tok);

        // Strip trailing <|im_end|> if split across tokens
        static const char* STOP = "<|im_end|>";
        if (out.size() >= 10 && out.compare(out.size() - 10, 10, STOP) == 0) {
            out.resize(out.size() - 10);
            break;
        }

        // Emit safe complete UTF-8 bytes to Java
        size_t safe_len = valid_utf8_prefix_len(utf8_pending);
        if (safe_len > 0) {
            std::string to_emit = utf8_pending.substr(0, safe_len);
            utf8_pending = utf8_pending.substr(safe_len);

            if (on_token && !to_emit.empty()) {
                jstring jpiece = env->NewStringUTF(to_emit.c_str());
                env->CallVoidMethod(callback, on_token, jpiece);
                env->DeleteLocalRef(jpiece);
                if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            }
        }

        // Decode the generated token so next iteration has logits ready
        batch.n_tokens = 0;
        batch_add_token(batch, tok, n_cur++, true);
        if (llama_decode(nc->ctx, batch) != 0) {
            LOGE("llama_decode failed during generation step %d", i);
            break;
        }
    }

    llama_batch_free(batch);
    auto t_gen_end = std::chrono::steady_clock::now();
    double gen_ms = std::chrono::duration<double, std::milli>(t_gen_end - t_gen_start).count();
    double gen_tps = generated_count > 0 ? generated_count * 1000.0 / gen_ms : 0;
    LOGI("generation complete: %d tokens in %.1f ms (%.1f tok/s)", generated_count, gen_ms, gen_tps);

    // Flush any remaining UTF-8 bytes at completion
    if (!utf8_pending.empty() && on_token) {
        jstring jpiece = env->NewStringUTF(utf8_pending.c_str());
        env->CallVoidMethod(callback, on_token, jpiece);
        env->DeleteLocalRef(jpiece);
        if (env->ExceptionCheck()) { env->ExceptionClear(); }
    }

    return env->NewStringUTF(out.c_str());
}

} // extern "C"
