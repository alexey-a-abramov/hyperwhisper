#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <algorithm>
#include <chrono>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Version marker for native library - update this to force rebuild
#define JNI_VERSION "1.2.1-callbacks-v2"

// Global reference to Java VM and callback objects
static JavaVM* g_jvm = nullptr;
static jobject g_progress_callback = nullptr;
static jobject g_segment_callback = nullptr;

// Mutex for callback safety
static std::mutex g_callback_mutex;

/**
 * Initialize JNI on library load
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/**
 * Cleanup on library unload
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    if (g_progress_callback != nullptr) {
        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_progress_callback);
        }
        g_progress_callback = nullptr;
    }

    if (g_segment_callback != nullptr) {
        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_segment_callback);
        }
        g_segment_callback = nullptr;
    }
}

/**
 * Get optimal number of threads for whisper processing.
 * Uses available cores but caps at a reasonable maximum to avoid
 * memory pressure and diminishing returns on mobile devices.
 */
static int get_optimal_threads() {
    int available_cores = std::thread::hardware_concurrency();
    if (available_cores == 0) {
        // hardware_concurrency() failed, use safe default
        return 4;
    }
    // Use most available cores but leave 1-2 for system responsiveness
    // Cap at 8 to avoid excessive memory usage on high-core devices
    int optimal = std::min(available_cores - 1, 8);
    return std::max(optimal, 2); // At least 2 threads
}

// Global context handle
static struct whisper_context* g_context = nullptr;

// Forward declaration
extern bool read_wav(const char* filename, std::vector<float>& pcm_data, int& sample_rate);

/**
 * Progress callback - called by whisper.cpp during processing
 */
static void progress_callback(struct whisper_context* ctx, struct whisper_state* state, int progress, void* user_data) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    if (g_progress_callback == nullptr || g_jvm == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    JavaVMAttachArgs args = { JNI_VERSION_1_6, "WhisperProgressThread", nullptr };

    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_jvm->AttachCurrentThread(&env, &args) != JNI_OK) {
            LOGE("Failed to attach thread to JVM");
            return;
        }
    }

    jclass callback_class = env->GetObjectClass(g_progress_callback);
    if (callback_class == nullptr) {
        g_jvm->DetachCurrentThread();
        return;
    }

    jmethodID on_progress_method = env->GetMethodID(callback_class, "onProgress", "(I)V");
    if (on_progress_method != nullptr) {
        env->CallVoidMethod(g_progress_callback, on_progress_method, (jint)progress);
    }

    env->DeleteLocalRef(callback_class);
    // Don't detach - keep thread attached for performance
}

/**
 * New segment callback - called when whisper.cpp completes a text segment
 */
static void new_segment_callback(struct whisper_context* ctx, struct whisper_state* state, int n_new, void* user_data) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    if (g_segment_callback == nullptr || g_jvm == nullptr) {
        return;
    }

    JNIEnv* env = nullptr;
    JavaVMAttachArgs args = { JNI_VERSION_1_6, "WhisperProgressThread", nullptr };

    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_jvm->AttachCurrentThread(&env, &args) != JNI_OK) {
            LOGE("Failed to attach thread to JVM");
            return;
        }
    }

    jclass callback_class = env->GetObjectClass(g_segment_callback);
    if (callback_class == nullptr) {
        g_jvm->DetachCurrentThread();
        return;
    }

    // Get the newly added segments
    const int n_segments = whisper_full_n_segments(ctx);

    for (int i = n_segments - n_new; i < n_segments; i++) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(ctx, i);
        int64_t t1 = whisper_full_get_segment_t1(ctx, i);

        jmethodID on_segment_method = env->GetMethodID(callback_class, "onSegment", "(Ljava/lang/String;JJ)V");
        if (on_segment_method != nullptr) {
            jstring jtext = env->NewStringUTF(text);
            env->CallVoidMethod(g_segment_callback, on_segment_method, jtext, (jlong)t0, (jlong)t1);
            env->DeleteLocalRef(jtext);
        }
    }

    env->DeleteLocalRef(callback_class);
    // Don't detach - keep thread attached for performance
}

extern "C" {

/**
 * Load whisper model from file path
 */
JNIEXPORT jboolean JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeLoadModel(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    // Release previous model if loaded
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }

    // Load model
    g_context = whisper_init_from_file(path);

    env->ReleaseStringUTFChars(modelPath, path);

    if (g_context == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

/**
 * Transcribe audio from WAV file
 */
JNIEXPORT jstring JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeTranscribe(
    JNIEnv* env,
    jobject thiz,
    jstring audioPath,
    jstring language,
    jboolean translate
) {
    if (g_context == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const char* audio_path = env->GetStringUTFChars(audioPath, nullptr);
    const char* lang = env->GetStringUTFChars(language, nullptr);

    LOGI("Transcribing: %s, language: %s, translate: %d", audio_path, lang, translate);

    // Read WAV file and extract PCM samples
    std::vector<float> pcm_data;
    int sample_rate = 0;
    if (!read_wav(audio_path, pcm_data, sample_rate)) {
        LOGE("Failed to read WAV file");
        env->ReleaseStringUTFChars(audioPath, audio_path);
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("");
    }

    // Calculate audio duration for logging
    float audio_duration_sec = (float)pcm_data.size() / (float)sample_rate;
    LOGI("Audio loaded: %zu samples, %d Hz (%.2f seconds)", pcm_data.size(), sample_rate, audio_duration_sec);

    // Get optimal thread count for this device
    int n_threads = get_optimal_threads();
    LOGI("WhisperJNI version %s - using %d threads for transcription", JNI_VERSION, n_threads);

    // Set up whisper parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = translate;
    params.n_threads = n_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    // Set up progress and segment callbacks
    std::lock_guard<std::mutex> lock(g_callback_mutex);
    if (g_progress_callback != nullptr) {
        params.progress_callback = progress_callback;
        params.progress_callback_user_data = nullptr;
        LOGI("Progress callback enabled");
    }
    if (g_segment_callback != nullptr) {
        params.new_segment_callback = new_segment_callback;
        params.new_segment_callback_user_data = nullptr;
        LOGI("Segment callback enabled");
    }

    // Set language if provided
    if (strlen(lang) > 0 && strcmp(lang, "auto") != 0) {
        params.language = lang;
    } else {
        params.language = "auto";
    }

    // Run inference with timing
    LOGI("[TIMING] Starting native transcription...");
    auto start_time = std::chrono::high_resolution_clock::now();

    int result = whisper_full(g_context, params, pcm_data.data(), pcm_data.size());

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    float real_time_factor = (duration_ms / 1000.0f) / audio_duration_sec;
    LOGI("[TIMING] Native transcription completed in %lld ms (%.2fx realtime)", duration_ms, real_time_factor);

    env->ReleaseStringUTFChars(audioPath, audio_path);
    env->ReleaseStringUTFChars(language, lang);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    // Extract text from segments
    std::string transcription;
    const int n_segments = whisper_full_n_segments(g_context);
    LOGI("Transcription complete: %d segments", n_segments);

    for (int i = 0; i < n_segments; i++) {
        const char* text = whisper_full_get_segment_text(g_context, i);
        transcription += text;
    }

    LOGI("Final transcription: %zu chars", transcription.length());
    return env->NewStringUTF(transcription.c_str());
}

/**
 * Unload model and free resources
 */
JNIEXPORT void JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeUnloadModel(
    JNIEnv* env,
    jobject thiz
) {
    if (g_context != nullptr) {
        LOGI("Unloading model");
        whisper_free(g_context);
        g_context = nullptr;
    }
}

/**
 * Check if model is loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeIsModelLoaded(
    JNIEnv* env,
    jobject thiz
) {
    return g_context != nullptr ? JNI_TRUE : JNI_FALSE;
}

/**
 * Set progress callback for real-time transcription updates
 * @param progressCallback Object implementing onProgress(I)V
 */
JNIEXPORT void JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeSetProgressCallback(
    JNIEnv* env,
    jobject thiz,
    jobject progressCallback
) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    // Clear previous callback
    if (g_progress_callback != nullptr) {
        env->DeleteGlobalRef(g_progress_callback);
        g_progress_callback = nullptr;
    }

    // Set new callback
    if (progressCallback != nullptr) {
        g_progress_callback = env->NewGlobalRef(progressCallback);
        LOGI("Progress callback registered");
    } else {
        LOGI("Progress callback cleared");
    }
}

/**
 * Set segment callback for real-time text streaming
 * @param segmentCallback Object implementing onSegment(Ljava/lang/String;JJ)V
 */
JNIEXPORT void JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeSetSegmentCallback(
    JNIEnv* env,
    jobject thiz,
    jobject segmentCallback
) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    // Clear previous callback
    if (g_segment_callback != nullptr) {
        env->DeleteGlobalRef(g_segment_callback);
        g_segment_callback = nullptr;
    }

    // Set new callback
    if (segmentCallback != nullptr) {
        g_segment_callback = env->NewGlobalRef(segmentCallback);
        LOGI("Segment callback registered");
    } else {
        LOGI("Segment callback cleared");
    }
}

/**
 * Clear all callbacks
 */
JNIEXPORT void JNICALL
Java_com_hyperwhisper_native_1whisper_WhisperContext_nativeClearCallbacks(
    JNIEnv* env,
    jobject thiz
) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);

    if (g_progress_callback != nullptr) {
        env->DeleteGlobalRef(g_progress_callback);
        g_progress_callback = nullptr;
    }

    if (g_segment_callback != nullptr) {
        env->DeleteGlobalRef(g_segment_callback);
        g_segment_callback = nullptr;
    }

    LOGI("All callbacks cleared");
}

} // extern "C"
