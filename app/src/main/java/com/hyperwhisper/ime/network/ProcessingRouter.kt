package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure decision logic for which audio-processing path to take:
 *
 *  - whether the configured provider needs the two-step transcribe →
 *    post-process flow,
 *  - which [AudioProcessingStrategy] should handle a single-step request,
 *    accounting for on-device Whisper override.
 *
 * Extracted from [VoiceRepository] so the orchestration loop reads top-down
 * without 100+ lines of branching.
 */
@Singleton
class ProcessingRouter @Inject constructor(
    private val transcriptionStrategy: TranscriptionStrategy,
    private val chatCompletionStrategy: ChatCompletionStrategy,
    private val localProcessingStrategy: LocalProcessingStrategy,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ProcessingRouter"

        /**
         * Pure two-step decision logic, exposed on the companion so it can be
         * unit-tested without constructing the DI-heavy router. True if the
         * request needs transcription + LLM post-processing; false for
         * providers that handle audio + transformation in one chat call, or
         * when LLM post-processing is disabled and no translation is required.
         */
        fun needsTwoStep(
            voiceMode: VoiceMode,
            apiSettings: ApiSettings
        ): Boolean {
            // If LLM is disabled (NONE), no post-processing
            if (apiSettings.llmConfig.provider == LlmProvider.NONE) {
                return false
            }

            // Translation is only needed if output language is set AND different
            // from input. If both are the same (e.g., both "en"), no translation.
            val needsTranslation = apiSettings.outputLanguage.isNotEmpty() &&
                apiSettings.outputLanguage != apiSettings.inputLanguage

            // OpenRouter / Gemini / Antigravity handle audio + translation in one
            // chat call.
            if (apiSettings.provider == ApiProvider.OPENROUTER) return false
            if (apiSettings.provider == ApiProvider.GEMINI) return false
            if (apiSettings.provider == ApiProvider.ANTIGRAVITY) return false

            // Hugging Face is text-only — requires two-step for all audio input
            if (apiSettings.provider == ApiProvider.HUGGINGFACE) return true

            // Verbatim mode only needs post-processing if translation is required
            if (voiceMode.id == "verbatim") return needsTranslation

            // All other providers with transformation modes need two-step
            return true
        }
    }

    /** Instance delegate — see [Companion.needsTwoStep]. */
    fun needsTwoStepProcessing(
        voiceMode: VoiceMode,
        apiSettings: ApiSettings
    ): Boolean = needsTwoStep(voiceMode, apiSettings)

    /**
     * Pick the strategy for single-step processing. On-device Whisper takes
     * priority over any cloud provider when the user has marked it active,
     * which is why this method is `suspend` — it reads the live settings
     * snapshot.
     */
    suspend fun selectStrategy(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): AudioProcessingStrategy {
        val useLocal = settingsRepository.apiSettings.first().localModelSettings.useLocalWhisper
        if (useLocal) {
            Log.d(TAG, "Selected LocalProcessingStrategy (on-device Whisper active)")
            return localProcessingStrategy
        }
        return selectStrategyForCloud(voiceMode, provider)
    }

    private fun selectStrategyForCloud(
        voiceMode: VoiceMode,
        provider: ApiProvider
    ): AudioProcessingStrategy {
        return when {
            // OpenRouter always uses chat completion
            provider == ApiProvider.OPENROUTER -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (OpenRouter)")
                chatCompletionStrategy
            }
            // Gemini always uses chat completion (supports audio natively)
            provider == ApiProvider.GEMINI -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Gemini)")
                chatCompletionStrategy
            }
            // Antigravity uses OpenAI-compatible chat completion with OAuth-backed quota
            provider == ApiProvider.ANTIGRAVITY -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Antigravity)")
                chatCompletionStrategy
            }
            // Hugging Face always uses chat completion (text-only LLMs)
            provider == ApiProvider.HUGGINGFACE -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (HuggingFace - text-only)")
                chatCompletionStrategy
            }
            // Verbatim mode with transcription-style providers uses transcription
            voiceMode.id == "verbatim" && (provider == ApiProvider.OPENAI || provider == ApiProvider.GROQ || provider == ApiProvider.SELFHOSTED_WHISPER) -> {
                Log.d(TAG, "Selected TranscriptionStrategy (Verbatim)")
                transcriptionStrategy
            }
            // All transformations use chat completion
            else -> {
                Log.d(TAG, "Selected ChatCompletionStrategy (Transformation)")
                chatCompletionStrategy
            }
        }
    }
}
