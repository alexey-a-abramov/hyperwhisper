package com.hyperwhisper.ime.llm

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes local LLM rewrite calls to the right native engine based on the
 * picked model file extension:
 *   - `.gguf`               → [LlamaCppEngine] (vendored llama.cpp)
 *   - `.task` / `.litertlm` → [GemmaInferenceEngine] (MediaPipe LLM Inference)
 *   - `.bin`                → MediaPipe (legacy litert-community packaging)
 *
 * Single entry point so [com.hyperwhisper.network.LocalProcessingStrategy]
 * and [com.hyperwhisper.network.ConnectionTester] don't need to know which
 * runtime is in play.
 */
@Singleton
class LocalLlmRouter @Inject constructor(
    private val gemma: GemmaInferenceEngine,
    private val llama: LlamaCppEngine
) {
    companion object {
        private const val TAG = "LocalLlmRouter"
    }

    enum class Engine { GEMMA, LLAMA_CPP }

    fun engineFor(modelPath: String): Engine {
        val lower = modelPath.lowercase()
        return when {
            lower.endsWith(".gguf") -> Engine.LLAMA_CPP
            else -> Engine.GEMMA
        }
    }

    suspend fun rewrite(
        modelPath: String,
        systemPrompt: String,
        userText: String
    ): String {
        val engine = engineFor(modelPath)
        Log.d(TAG, "rewrite via $engine for ${modelPath.substringAfterLast('/')}")
        return when (engine) {
            Engine.LLAMA_CPP -> llama.rewrite(
                modelPath = modelPath,
                systemPrompt = systemPrompt,
                userText = userText
            )
            Engine.GEMMA -> gemma.rewrite(
                modelPath = modelPath,
                systemPrompt = systemPrompt,
                userText = userText
            )
        }
    }

    suspend fun releaseAll() {
        runCatching { gemma.release() }
        runCatching { llama.release() }
    }
}
