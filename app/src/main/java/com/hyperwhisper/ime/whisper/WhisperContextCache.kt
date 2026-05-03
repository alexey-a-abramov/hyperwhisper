package com.hyperwhisper.ime.whisper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide cache for [WhisperContext]. `whisper_init_from_file` takes 1–3 s
 * for a base model and several seconds for medium/large, so we hold one open
 * context per active model path and only re-load when the path changes.
 *
 * whisper_context isn't thread-safe; serialization is enforced inside
 * [WhisperContext.transcribe] via its single-thread dispatcher.
 */
@Singleton
class WhisperContextCache @Inject constructor() {

    companion object {
        private const val TAG = "WhisperContextCache"
    }

    private val mutex = Mutex()
    private var ctx: WhisperContext? = null
    private var ctxPath: String? = null

    suspend fun get(modelPath: String): WhisperContext = mutex.withLock {
        if (ctxPath == modelPath) {
            ctx?.let { return@withLock it }
        }
        // Either nothing cached or path changed — drop old, load new.
        ctx?.let { runCatching { it.release() } }
        ctxPath = null
        ctx = null
        Log.i(TAG, "Loading whisper.cpp context: $modelPath")
        val started = System.nanoTime()
        val fresh = withContext(Dispatchers.IO) {
            WhisperContext.createFromFile(modelPath)
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "Loaded whisper context in ${ms} ms")
        ctx = fresh
        ctxPath = modelPath
        fresh
    }

    suspend fun release() = mutex.withLock {
        ctx?.let { runCatching { it.release() } }
        ctx = null
        ctxPath = null
    }
}
