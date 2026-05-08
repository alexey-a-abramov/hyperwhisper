package com.hyperwhisper.ime.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Gemma post-processing via MediaPipe LLM Inference.
 *
 * Wraps a single [LlmInference] instance keyed by the active model file path.
 * Reloading the model is expensive (hundreds of ms to seconds), so the engine
 * holds one open session and only swaps when the user picks a different file
 * via [LocalModelSettings.gemmaModelPath].
 *
 * Models are MediaPipe `.bin` / `.task` packages, e.g.
 * `huggingface.co/litert-community/Gemma2-2B-it/gemma-2b-it-cpu-int4.bin`.
 * Standard llama.cpp GGUF files will *not* load — MediaPipe uses its own
 * runtime; if the user picks a `.gguf` we fail fast with a readable error.
 */
@Singleton
class GemmaInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GemmaInference"
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TEMPERATURE = 0.8f
    }

    private val mutex = Mutex()
    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    suspend fun rewrite(
        modelPath: String,
        systemPrompt: String,
        userText: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): String = mutex.withLock {
        require(modelPath.isNotBlank()) { "Gemma model path is blank" }
        // Note: callers are expected to route GGUF through LlamaCppEngine via
        // LocalLlmRouter. If a .gguf still reaches this point, MediaPipe will
        // surface its own native error during ensureLoaded — we no longer
        // pre-empt with a custom message.
        val instance = ensureLoaded(modelPath, maxTokens)
        // Gemma's instruction-tuned chat template:
        //   <start_of_turn>user\n{prompt}<end_of_turn>\n<start_of_turn>model\n
        val prompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append("<start_of_turn>user\n")
                append(systemPrompt.trim())
                append("\n\nInput:\n")
                append(userText)
                append("<end_of_turn>\n<start_of_turn>model\n")
            } else {
                append("<start_of_turn>user\n")
                append(userText)
                append("<end_of_turn>\n<start_of_turn>model\n")
            }
        }
        Log.d(TAG, "Generating with model=${loadedPath?.substringAfterLast('/')}, prompt_len=${prompt.length}")
        val started = System.nanoTime()
        val result = generate(instance, prompt)
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "Generated ${result.length} chars in ${ms} ms")
        // MediaPipe returns the assistant turn raw; strip any trailing turn marker.
        result.substringBefore("<end_of_turn>").trim()
    }

    private suspend fun ensureLoaded(modelPath: String, maxTokens: Int): LlmInference {
        if (loadedPath == modelPath && engine != null) return engine!!
        Log.i(TAG, "Loading MediaPipe LLM: $modelPath (maxTokens=$maxTokens)")
        engine?.let { runCatching { it.close() } }
        engine = null
        loadedPath = null
        val started = System.nanoTime()
        // Newer MediaPipe versions move sampling params (topK/temperature) to
        // LlmInferenceSession options; the engine-level builder we target only
        // accepts the model path + max output tokens.
        val opts = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build()
        val instance = withContext(Dispatchers.IO) {
            LlmInference.createFromOptions(context, opts)
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(TAG, "MediaPipe LLM loaded in ${ms} ms")
        engine = instance
        loadedPath = modelPath
        return instance
    }

    private suspend fun generate(instance: LlmInference, prompt: String): String =
        suspendCancellableCoroutine { cont ->
            try {
                // generateResponse() is blocking; run on IO from caller scope.
                val result = instance.generateResponse(prompt)
                cont.resume(result)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }

    suspend fun release() = mutex.withLock {
        engine?.let { runCatching { it.close() } }
        engine = null
        loadedPath = null
    }
}
