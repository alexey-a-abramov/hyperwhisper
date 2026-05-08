// JNI bridge between LlamaCppEngine.kt and the vendored llama.cpp.
//
// One-model-at-a-time design (mirrors GemmaInferenceEngine): a global
// state struct holds the loaded model + context. The Kotlin side
// serialises calls with a Mutex, so this file does not need its own
// locking — it just needs to be safe against the cancellation flag
// being flipped from another thread mid-decode.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct State {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
    std::string     loadedPath;
    std::atomic<bool> cancelFlag{false};
    bool backendReady = false;
};

State g_state;

bool ggml_abort_cancel(void * /*user*/) {
    return g_state.cancelFlag.load(std::memory_order_relaxed);
}

void ensure_backend() {
    if (!g_state.backendReady) {
        llama_backend_init();
        g_state.backendReady = true;
    }
}

void release_locked() {
    if (g_state.ctx)   { llama_free(g_state.ctx); g_state.ctx = nullptr; }
    if (g_state.model) { llama_model_free(g_state.model); g_state.model = nullptr; }
    g_state.loadedPath.clear();
}

std::string token_to_str(const llama_vocab * vocab, llama_token id) {
    char buf[256];
    int32_t n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, /*special=*/false);
    if (n < 0) return {};
    return std::string(buf, buf + n);
}

// Apply the model's built-in chat template (gemma/llama/qwen/mistral/...
// auto-detected from the GGUF metadata). Falls back to a generic
// `system\nuser\n` shape when the model has no template — that's still
// a working prompt for instruction-tuned models.
std::string apply_chat_template(
        const llama_model * model,
        const std::string & system_prompt,
        const std::string & user_text) {
    const char * tmpl = llama_model_chat_template(model, /*name=*/nullptr);

    std::vector<llama_chat_message> msgs;
    if (!system_prompt.empty()) {
        msgs.push_back({"system", system_prompt.c_str()});
    }
    msgs.push_back({"user", user_text.c_str()});

    if (tmpl == nullptr) {
        // No built-in template — emit a plain prompt that most chat
        // models still understand.
        std::string out;
        if (!system_prompt.empty()) {
            out += system_prompt;
            out += "\n\n";
        }
        out += user_text;
        return out;
    }

    // Allocate ~2 * total chars per docstring guidance, retry if truncated.
    size_t alloc = (system_prompt.size() + user_text.size() + 64) * 2;
    if (alloc < 1024) alloc = 1024;
    std::vector<char> buf(alloc);
    int32_t written = llama_chat_apply_template(
            tmpl, msgs.data(), msgs.size(), /*add_ass=*/true,
            buf.data(), (int32_t) buf.size());
    if (written < 0) {
        LOGW("chat template apply failed (%d)", written);
        return user_text;
    }
    if ((size_t) written > buf.size()) {
        buf.resize(written + 1);
        written = llama_chat_apply_template(
                tmpl, msgs.data(), msgs.size(), true,
                buf.data(), (int32_t) buf.size());
        if (written < 0) {
            LOGW("chat template apply (resize) failed (%d)", written);
            return user_text;
        }
    }
    return std::string(buf.data(), buf.data() + written);
}

void llama_log_relay(ggml_log_level lvl, const char * text, void * /*user*/) {
    int prio = ANDROID_LOG_INFO;
    switch (lvl) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
        case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        default: break;
    }
    __android_log_write(prio, LOG_TAG, text);
}

} // namespace

// ----- JNI surface --------------------------------------------------------

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hyperwhisper_ime_llm_LlamaCppEngine_nativeInit(
        JNIEnv * env, jobject /*thiz*/,
        jstring jModelPath, jint nCtx, jint nThreads) {

    ensure_backend();
    llama_log_set(llama_log_relay, nullptr);

    const char * cPath = env->GetStringUTFChars(jModelPath, nullptr);
    std::string path(cPath);
    env->ReleaseStringUTFChars(jModelPath, cPath);

    if (g_state.loadedPath == path && g_state.model && g_state.ctx) {
        return JNI_TRUE;  // already loaded
    }
    release_locked();

    LOGI("loading model: %s (n_ctx=%d, n_threads=%d)", path.c_str(), (int) nCtx, (int) nThreads);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU-only; Vulkan path bumps this in phase 2
    mparams.use_mmap     = true;
    mparams.use_mlock    = false;

    g_state.model = llama_model_load_from_file(path.c_str(), mparams);
    if (g_state.model == nullptr) {
        LOGE("llama_model_load_from_file failed for %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx              = nCtx > 0 ? (uint32_t) nCtx : 4096;
    cparams.n_batch            = 512;
    cparams.n_threads          = nThreads > 0 ? nThreads : 4;
    cparams.n_threads_batch    = cparams.n_threads;
    cparams.abort_callback     = ggml_abort_cancel;
    cparams.abort_callback_data = nullptr;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (g_state.ctx == nullptr) {
        LOGE("llama_init_from_model failed");
        llama_model_free(g_state.model);
        g_state.model = nullptr;
        return JNI_FALSE;
    }

    g_state.loadedPath = path;
    g_state.cancelFlag.store(false);
    LOGI("model loaded ok");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_hyperwhisper_ime_llm_LlamaCppEngine_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/) {
    release_locked();
}

JNIEXPORT void JNICALL
Java_com_hyperwhisper_ime_llm_LlamaCppEngine_nativeCancel(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_state.cancelFlag.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_hyperwhisper_ime_llm_LlamaCppEngine_nativeGenerate(
        JNIEnv * env, jobject /*thiz*/,
        jstring jSystem, jstring jUser,
        jint maxTokens, jfloat temperature, jint topK, jfloat topP, jint seed) {

    if (g_state.model == nullptr || g_state.ctx == nullptr) {
        LOGE("nativeGenerate called with no model loaded");
        return nullptr;
    }

    const char * cSys  = env->GetStringUTFChars(jSystem, nullptr);
    const char * cUser = env->GetStringUTFChars(jUser, nullptr);
    std::string system_prompt(cSys);
    std::string user_text(cUser);
    env->ReleaseStringUTFChars(jSystem, cSys);
    env->ReleaseStringUTFChars(jUser,   cUser);

    g_state.cancelFlag.store(false);

    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    std::string prompt = apply_chat_template(g_state.model, system_prompt, user_text);

    // Tokenise. Two-pass — first call returns negative count == buffer needed.
    std::vector<llama_token> tokens(prompt.size() + 16);
    int32_t n_tok = llama_tokenize(
            vocab, prompt.c_str(), (int32_t) prompt.size(),
            tokens.data(), (int32_t) tokens.size(),
            /*add_special=*/true, /*parse_special=*/true);
    if (n_tok < 0) {
        tokens.resize(-n_tok);
        n_tok = llama_tokenize(
                vocab, prompt.c_str(), (int32_t) prompt.size(),
                tokens.data(), (int32_t) tokens.size(),
                true, true);
    }
    if (n_tok <= 0) {
        LOGE("tokenization failed (%d)", n_tok);
        return nullptr;
    }
    tokens.resize(n_tok);

    // Sampler: temp + top_k + top_p + dist (random).
    // Greedy = temperature == 0 (skip stochastic samplers).
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        if (topK > 0)            llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        if (topP > 0.0f && topP < 1.0f) llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist((uint32_t) seed));
    }

    std::string output;
    output.reserve(maxTokens * 4);

    // Prefill: feed the prompt as one batch.
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(g_state.ctx, batch) != 0) {
        LOGE("prefill llama_decode failed");
        llama_sampler_free(smpl);
        return nullptr;
    }

    int generated = 0;
    llama_token cur = 0;
    while (generated < maxTokens) {
        if (g_state.cancelFlag.load()) {
            LOGW("generation cancelled");
            break;
        }
        cur = llama_sampler_sample(smpl, g_state.ctx, -1);
        if (llama_vocab_is_eog(vocab, cur)) break;

        output += token_to_str(vocab, cur);
        generated++;

        // Decode the just-sampled token to advance the KV cache.
        llama_batch nextBatch = llama_batch_get_one(&cur, 1);
        if (llama_decode(g_state.ctx, nextBatch) != 0) {
            LOGE("decode failed mid-generation at token %d", generated);
            break;
        }
    }

    llama_sampler_free(smpl);

    LOGI("generated %d tokens, %zu chars", generated, output.size());
    return env->NewStringUTF(output.c_str());
}

} // extern "C"
