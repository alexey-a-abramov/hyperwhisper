package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import com.hyperwhisper.localization.stringsFor
import com.hyperwhisper.utils.TraceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Strategy C: Local Processing (On-device)
 *
 * On-device transcription via whisper.cpp (JNI). Decode the recorder's M4A/AAC
 * (or any container MediaCodec accepts) into 16 kHz mono float32 PCM, push it
 * through a cached [WhisperContext], optionally pass through Gemma for
 * voice-mode-aware post-processing (polite/casual/translation/etc.) via the
 * [GemmaInferenceEngine] when [LocalModelSettings.useLocalGemma] is on.
 */
class LocalProcessingStrategy(
    private val settingsRepository: com.hyperwhisper.data.SettingsRepository,
    private val whisperCache: com.hyperwhisper.ime.whisper.WhisperContextCache,
    private val localLlm: com.hyperwhisper.ime.llm.LocalLlmRouter
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "LocalProcessingStrategy"
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        val strings = stringsFor(
            settingsRepository.appearanceSettings.first().uiLanguage
        )
        return try {
            Log.d(TAG, "========== LOCAL PROCESSING REQUEST ==========")
            val settings = settingsRepository.apiSettings.first()
            val localSettings = settings.localModelSettings

            if (localSettings.whisperModelPath.isEmpty()) {
                return ApiResult.Error(strings.errorLocalWhisperPathMissing)
            }
            val whisperModel = File(localSettings.whisperModelPath)
            if (!whisperModel.exists()) {
                return ApiResult.Error(
                    String.format(
                        strings.errorLocalWhisperModelNotFoundFormat,
                        localSettings.whisperModelPath
                    )
                )
            }

            Log.d(TAG, "Local transcription: model=${whisperModel.name}, audio=${audioFile.name} (${audioFile.length()} B)")
            val startTime = System.currentTimeMillis()

            val ctx = whisperCache.get(localSettings.whisperModelPath)

            val decodeStart = System.currentTimeMillis()
            val samples = withContext(Dispatchers.Default) {
                com.hyperwhisper.ime.audio.AudioDecoder.decodeTo16kMonoFloat(audioFile)
            }
            val decodeMs = System.currentTimeMillis() - decodeStart
            val audioSeconds = samples.size.toDouble() /
                com.hyperwhisper.ime.audio.AudioDecoder.WHISPER_SAMPLE_RATE
            Log.d(TAG, "Decoded ${samples.size} samples (${"%.2f".format(audioSeconds)} s) in ${decodeMs} ms")

            val inferenceStart = System.currentTimeMillis()
            val language = settings.inputLanguage.takeIf { it.isNotBlank() }
            val translate = settings.outputLanguage.equals("en", ignoreCase = true) &&
                language != null && !language.equals("en", ignoreCase = true)
            val rawText = ctx.transcribe(
                samples = samples,
                language = language,
                translate = translate
            ).trim()
            val transcriptionTime = System.currentTimeMillis() - inferenceStart
            Log.d(TAG, "whisper_full done in ${transcriptionTime} ms; len=${rawText.length}")

            if (rawText.isEmpty()) {
                return ApiResult.Error(strings.errorLocalWhisperEmptyResult)
            }

            // Local LLM post-processing — Gemma rewrites the raw transcription
            // according to the voice mode's system prompt (polite/casual/etc.)
            // when the user has set a Gemma model and switched on the toggle.
            // For "verbatim" / "direct" modes we skip; the raw text is the goal.
            val skipLocalLlm = voiceMode.processingMode == "direct" ||
                voiceMode.id.equals("verbatim", ignoreCase = true) ||
                voiceMode.systemPrompt.isBlank()
            val canRunLocalLlm = localSettings.useLocalGemma &&
                localSettings.gemmaModelPath.isNotBlank() &&
                File(localSettings.gemmaModelPath).exists()

            var finalResult = rawText
            var postProcessingTimeMs: Long? = null
            var postProcessingModelName: String? = null

            if (!skipLocalLlm && canRunLocalLlm) {
                val modelName = File(localSettings.gemmaModelPath).name
                Log.d(TAG, "Local LLM post-processing with $modelName")
                val ppStart = System.currentTimeMillis()
                try {
                    val rewritten = localLlm.rewrite(
                        modelPath = localSettings.gemmaModelPath,
                        systemPrompt = voiceMode.systemPrompt,
                        userText = rawText
                    )
                    if (rewritten.isNotBlank()) finalResult = rewritten
                    postProcessingTimeMs = System.currentTimeMillis() - ppStart
                    postProcessingModelName = modelName
                    Log.d(TAG, "Local LLM done in ${postProcessingTimeMs} ms")
                } catch (t: Throwable) {
                    // Don't fail the whole transcription if the local LLM blows
                    // up — the raw Whisper text is still useful.
                    TraceLogger.error(TAG, "Local LLM post-processing failed; returning raw transcription", t)
                }
            } else if (canRunLocalLlm) {
                Log.d(TAG, "Skipping local LLM — voice mode is verbatim/direct")
            }

            val totalTime = System.currentTimeMillis() - startTime

            val processingInfo = ProcessingInfo(
                processingMode = if (postProcessingModelName != null) "two-step" else "single-step",
                strategy = "local",
                transcriptionModel = whisperModel.name,
                postProcessingModel = postProcessingModelName,
                voiceModeName = voiceMode.name,
                systemPrompt = voiceMode.systemPrompt,
                audioDurationSeconds = audioSeconds,
                processingTimeMs = totalTime,
                transcriptionTimeMs = transcriptionTime,
                postProcessingTimeMs = postProcessingTimeMs,
                audioFileSizeBytes = audioFile.length()
            )

            Log.d(TAG, "✓ Local processing complete in ${totalTime}ms (decode=${decodeMs}, whisper=${transcriptionTime})")
            ApiResult.Success(finalResult, processingInfo)

        } catch (e: Exception) {
            TraceLogger.error(TAG, "Error in local processing", e)
            ApiResult.Error(
                String.format(
                    strings.errorLocalProcessingFailedFormat,
                    e.javaClass.simpleName,
                    e.message ?: strings.errorUnknown
                ),
                e
            )
        } catch (t: Throwable) {
            // Local inference can OOM (large models) or hit UnsatisfiedLinkError
            // (missing native lib). Convert to a graceful ApiResult.Error so the
            // IME doesn't crash on misconfigured local models.
            TraceLogger.error(TAG, "Fatal error in local inference — converted to ApiResult.Error", t)
            ApiResult.Error(
                String.format(
                    strings.errorLocalInferenceFailedFormat,
                    t.javaClass.simpleName,
                    t.message ?: strings.errorUnknown
                )
            )
        }
    }
}
