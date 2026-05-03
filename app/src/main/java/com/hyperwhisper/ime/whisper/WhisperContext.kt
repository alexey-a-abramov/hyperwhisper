package com.hyperwhisper.ime.whisper

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val LOG_TAG = "WhisperContext"

/**
 * Kotlin wrapper around whisper.cpp via JNI.
 *
 * Adapted from whisper.cpp/examples/whisper.android/lib/src/main/java/com/whispercpp/whisper/LibWhisper.kt
 * (MIT). Trimmed to file-based loading + transcribe — we don't need asset/input-stream
 * loaders on this codepath. Operations are serialized through a single-thread
 * dispatcher because whisper_context is not thread-safe.
 */
class WhisperContext private constructor(private var ptr: Long) {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    /**
     * Run whisper on a 16 kHz mono float32 buffer. Returns the concatenated
     * segment text (no timestamps).
     *
     * @param language ISO-639-1 code or null for auto-detect.
     * @param translate translate to English instead of transcribing.
     */
    suspend fun transcribe(
        samples: FloatArray,
        threads: Int = WhisperCpuConfig.preferredThreadCount,
        language: String? = null,
        translate: Boolean = false
    ): String = withContext(scope.coroutineContext) {
        require(ptr != 0L) { "WhisperContext has been released" }
        val rc = WhisperLib.fullTranscribe(ptr, threads, samples, language, translate)
        if (rc != 0) error("whisper_full failed with rc=$rc")
        val n = WhisperLib.getTextSegmentCount(ptr)
        buildString {
            for (i in 0 until n) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    @Suppress("ProtectedInFinal")
    protected fun finalize() {
        runBlocking { release() }
    }

    companion object {
        fun createFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) error("whisper_init_from_file failed for $filePath")
            return WhisperContext(ptr)
        }

        fun systemInfo(): String = try {
            WhisperLib.getSystemInfo()
        } catch (t: Throwable) {
            "unavailable: ${t.message}"
        }
    }
}

@Keep
private class WhisperLib {
    @Keep
    companion object {
        init {
            try {
                Log.d(LOG_TAG, "Loading libwhisper.so")
                System.loadLibrary("whisper")
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Failed to load libwhisper.so", t)
                throw t
            }
        }

        @JvmStatic external fun initContext(modelPath: String): Long
        @JvmStatic external fun freeContext(contextPtr: Long)
        @JvmStatic external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String?,
            translate: Boolean
        ): Int
        @JvmStatic external fun getTextSegmentCount(contextPtr: Long): Int
        @JvmStatic external fun getTextSegment(contextPtr: Long, index: Int): String
        @JvmStatic external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        @JvmStatic external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        @JvmStatic external fun getSystemInfo(): String
    }
}
