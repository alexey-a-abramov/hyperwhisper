#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global context handle
static struct whisper_context* g_context = nullptr;

// Forward declaration
extern bool read_wav(const char* filename, std::vector<float>& pcm_data, int& sample_rate);

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

    LOGI("Audio loaded: %zu samples, %d Hz", pcm_data.size(), sample_rate);

    // Set up whisper parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = translate;
    params.n_threads = 4; // Use 4 threads for mobile
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    // Set language if provided
    if (strlen(lang) > 0 && strcmp(lang, "auto") != 0) {
        params.language = lang;
    } else {
        params.language = "auto";
    }

    // Run inference
    LOGI("Starting transcription...");
    int result = whisper_full(g_context, params, pcm_data.data(), pcm_data.size());

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

} // extern "C"
