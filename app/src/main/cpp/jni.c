// JNI wrapper for whisper.cpp. Adapted from
// whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c
// (MIT-licensed). Package renamed to com.hyperwhisper.ime.whisper and
// trimmed to file-based loading + transcribe — we don't need asset/input-stream
// loaders on this codepath.

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "HyperWhisperJNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Kotlin emits @JvmStatic externals as direct static methods on the outer
// class, so the JNI-mangled names skip the "_00024Companion_" segment.
#define WHISPER_FN(name) Java_com_hyperwhisper_ime_whisper_WhisperLib_##name

JNIEXPORT jlong JNICALL
WHISPER_FN(initContext)(JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *model_path = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    LOGI("initContext('%s')", model_path);
    struct whisper_context *ctx =
        whisper_init_from_file_with_params(model_path, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
WHISPER_FN(freeContext)(JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    if (ctx) whisper_free(ctx);
}

JNIEXPORT jint JNICALL
WHISPER_FN(fullTranscribe)(JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
                            jfloatArray audio_data, jstring language_str, jboolean translate) {
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    if (!ctx) return -1;

    jfloat *audio = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize n = (*env)->GetArrayLength(env, audio_data);

    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime  = false;
    p.print_progress  = false;
    p.print_timestamps = false;
    p.print_special   = false;
    p.translate       = (bool) translate;
    p.n_threads       = num_threads;
    p.offset_ms       = 0;
    p.no_context      = true;
    p.single_segment  = false;

    const char *language = NULL;
    if (language_str != NULL) {
        language = (*env)->GetStringUTFChars(env, language_str, NULL);
        if (language && language[0] != '\0') {
            p.language = language;
        }
    }

    whisper_reset_timings(ctx);
    int rc = whisper_full(ctx, p, audio, n);
    if (rc != 0) {
        LOGW("whisper_full failed: %d", rc);
    }

    if (language != NULL) {
        (*env)->ReleaseStringUTFChars(env, language_str, language);
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
WHISPER_FN(getTextSegmentCount)(JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    if (!ctx) return 0;
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jstring JNICALL
WHISPER_FN(getTextSegment)(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    if (!ctx) return (*env)->NewStringUTF(env, "");
    const char *text = whisper_full_get_segment_text(ctx, index);
    return (*env)->NewStringUTF(env, text ? text : "");
}

JNIEXPORT jlong JNICALL
WHISPER_FN(getTextSegmentT0)(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    return ctx ? whisper_full_get_segment_t0(ctx, index) : 0;
}

JNIEXPORT jlong JNICALL
WHISPER_FN(getTextSegmentT1)(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    return ctx ? whisper_full_get_segment_t1(ctx, index) : 0;
}

JNIEXPORT jstring JNICALL
WHISPER_FN(getSystemInfo)(JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    const char *info = whisper_print_system_info();
    return (*env)->NewStringUTF(env, info ? info : "");
}
