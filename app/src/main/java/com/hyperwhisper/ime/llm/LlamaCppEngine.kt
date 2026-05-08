package com.hyperwhisper.ime.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device GGUF inference via the vendored llama.cpp build.
 *
 * Public surface mirrors [GemmaInferenceEngine.rewrite] so callers can swap
 * the two via [LocalLlmRouter] based on the chosen model file extension.
 *
 * Backed by `libllama.so` (built out-of-band by `app/scripts/build-llama-so.sh`).
 * The native side keeps a single global model+context — switching files calls
 * load again, which frees the previous instance. CPU-only for now; Vulkan
 * acceleration lands as a follow-up commit on this branch.
 */
@Singleton
class LlamaCppEngine @Inject constructor() {
    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TEMPERATURE = 0.8f
        private const val DEFAULT_CTX = 4096

        @Volatile
        private var libLoaded = false

        private fun ensureLib() {
            if (!libLoaded) {
                synchronized(this) {
                    if (!libLoaded) {
                        // libc++_shared is bundled alongside libwhisper.so by the
                        // whisper build script; load it explicitly so the dynamic
                        // linker resolves llama.cpp's C++ runtime references
                        // even on devices where the system loader does not
                        // auto-resolve sibling .so files.
                        runCatching { System.loadLibrary("c++_shared") }
                        System.loadLibrary("llama")
                        libLoaded = true
                    }
                }
            }
        }
    }

    private val mutex = Mutex()
    private var loadedPath: String? = null

    private external fun nativeInit(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeFree()
    private external fun nativeCancel()
    private external fun nativeGenerate(
        systemPrompt: String,
        userText: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        seed: Int
    ): String?

    suspend fun rewrite(
        modelPath: String,
        systemPrompt: String,
        userText: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        temperature: Float = DEFAULT_TEMPERATURE,
        topK: Int = DEFAULT_TOP_K,
        topP: Float = DEFAULT_TOP_P
    ): String = mutex.withLock {
        require(modelPath.isNotBlank()) { "Llama model path is blank" }
        ensureLib()

        if (loadedPath != modelPath) {
            Log.i(TAG, "Loading GGUF: $modelPath")
            val ok = withContext(Dispatchers.IO) {
                nativeInit(modelPath, DEFAULT_CTX, recommendedThreads())
            }
            if (!ok) {
                error("llama.cpp failed to load $modelPath — check that the file is a valid GGUF (run via logcat for details).")
            }
            loadedPath = modelPath
        }

        Log.d(TAG, "Generating: prompt_len=${userText.length}")
        val started = System.nanoTime()
        val result = withContext(Dispatchers.IO) {
            nativeGenerate(
                systemPrompt.trim(),
                userText,
                maxTokens,
                temperature,
                topK,
                topP,
                /*seed=*/ System.currentTimeMillis().toInt()
            )
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        if (result == null) {
            error("llama.cpp generation failed (returned null) — check logcat.")
        }
        Log.i(TAG, "Generated ${result.length} chars in ${ms} ms")
        result.trim()
    }

    suspend fun release() = mutex.withLock {
        if (loadedPath != null) {
            runCatching { nativeFree() }
            loadedPath = null
        }
    }

    fun cancelInflight() {
        if (libLoaded) runCatching { nativeCancel() }
    }

    private fun recommendedThreads(): Int {
        // Pick a sensible default that scales with the device. llama.cpp does
        // not benefit much past ~half the physical cores on phones (P+E core
        // mix where E-cores hurt throughput). Cap at 4.
        val avail = Runtime.getRuntime().availableProcessors()
        return (avail / 2).coerceIn(2, 4)
    }
}
