package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import com.hyperwhisper.native_whisper.AudioConverter
import com.hyperwhisper.native_whisper.WhisperContext
import com.hyperwhisper.native_whisper.WhisperProgressCallback
import com.hyperwhisper.native_whisper.WhisperSegmentCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local on-device processing using whisper.cpp
 * Implements the AudioProcessingStrategy interface for offline transcription
 */
@Singleton
class LocalWhisperStrategy @Inject constructor(
    private val whisperContext: WhisperContext,
    private val audioConverter: AudioConverter,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : AudioProcessingStrategy, LocalWhisperCallbacks {

    companion object {
        private const val TAG = "LocalWhisperStrategy"
    }

    // Thread-safe callback references
    private val progressCallbackRef = AtomicReference<WhisperProgressCallback?>(null)
    private val segmentCallbackRef = AtomicReference<WhisperSegmentCallback?>(null)

    /**
     * Set callbacks for real-time progress and segment streaming
     * Call this before processAudio to enable real-time updates
     */
    override fun setCallbacks(
        progressCallback: WhisperProgressCallback?,
        segmentCallback: WhisperSegmentCallback?
    ) {
        progressCallbackRef.set(progressCallback)
        segmentCallbackRef.set(segmentCallback)
        Log.d(TAG, "Callbacks registered: progress=${progressCallback != null}, segment=${segmentCallback != null}")
    }

    /**
     * Clear all callbacks
     */
    override fun clearCallbacks() {
        progressCallbackRef.set(null)
        segmentCallbackRef.set(null)
        whisperContext.clearCallbacks()
        Log.d(TAG, "Callbacks cleared")
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String, // Not used for local processing
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val totalStartTime = System.currentTimeMillis()

            Log.d(TAG, "========== LOCAL WHISPER PROCESSING START ==========")
            Log.d(TAG, "[TIMING] Processing started at: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())}")
            Log.d(TAG, "Processing audio with local whisper.cpp")
            Log.d(TAG, "Model: $modelId")
            Log.d(TAG, "Voice Mode: ${voiceMode.name}")
            Log.d(TAG, "Audio file: ${audioFile.name} (${audioFile.length()} bytes)")

            // 1. Get selected model from modelId
            val model = WhisperModel.values().find { it.modelName == modelId }
                ?: WhisperModel.TINY // Default to TINY if not found

            Log.d(TAG, "Using model: ${model.displayName}")

            // 2. Check if model is downloaded
            if (!modelRepository.isModelDownloaded(model)) {
                val error = "Model '${model.displayName}' is not downloaded. Please download it in settings."
                Log.e(TAG, error)
                return@withContext ApiResult.Error(error)
            }

            // 3. Load model if not already loaded
            val modelFile = modelRepository.getModelFile(model)
            var modelLoadTimeMs = 0L
            if (!whisperContext.isModelLoaded()) {
                Log.d(TAG, "[TIMING] Model loading started...")
                val modelLoadStart = System.currentTimeMillis()
                Log.d(TAG, "Loading model: ${modelFile.absolutePath}")
                val loadResult = whisperContext.loadModel(modelFile)
                modelLoadTimeMs = System.currentTimeMillis() - modelLoadStart
                if (loadResult.isFailure) {
                    val error = loadResult.exceptionOrNull()?.message ?: "Failed to load model"
                    Log.e(TAG, "Model loading failed: $error")
                    return@withContext ApiResult.Error("Failed to load model: $error")
                }
                Log.d(TAG, "[TIMING] Model loaded in ${modelLoadTimeMs}ms (${String.format("%.2f", modelLoadTimeMs / 1000.0)}s)")
            } else {
                Log.d(TAG, "Model already loaded (0ms)")
            }

            // 4. Convert M4A to WAV if needed
            var conversionTimeMs = 0L
            val wavFile = if (audioFile.extension.lowercase() == "m4a") {
                Log.d(TAG, "[TIMING] Audio conversion started...")
                val conversionStart = System.currentTimeMillis()
                Log.d(TAG, "Converting M4A to WAV...")
                val convertResult = audioConverter.convertM4AToWav(audioFile, audioFile.parentFile!!)
                conversionTimeMs = System.currentTimeMillis() - conversionStart
                if (convertResult.isFailure) {
                    val error = convertResult.exceptionOrNull()?.message ?: "Conversion failed"
                    Log.e(TAG, "Audio conversion failed: $error")
                    return@withContext ApiResult.Error("Audio conversion failed: $error")
                }
                val wav = convertResult.getOrNull()!!
                Log.d(TAG, "[TIMING] Audio converted in ${conversionTimeMs}ms (${String.format("%.2f", conversionTimeMs / 1000.0)}s)")
                Log.d(TAG, "  WAV file: ${wav.name} (${wav.length()} bytes)")
                wav
            } else {
                Log.d(TAG, "Audio is already in WAV format (0ms conversion)")
                audioFile
            }

            // 5. Get language settings
            val apiSettings = settingsRepository.apiSettings.first()
            val language = if (apiSettings.inputLanguage.isEmpty()) {
                "auto"
            } else {
                apiSettings.inputLanguage
            }

            Log.d(TAG, "Language: $language")

            // 6. Set up callbacks for real-time updates
            val progressCallback = progressCallbackRef.get()
            val segmentCallback = segmentCallbackRef.get()

            if (progressCallback != null) {
                whisperContext.setProgressCallback(progressCallback)
                Log.d(TAG, "Progress callback enabled for transcription")
            }
            if (segmentCallback != null) {
                whisperContext.setSegmentCallback(segmentCallback)
                Log.d(TAG, "Segment callback enabled for transcription")
            }

            // 7. Transcribe with whisper.cpp
            Log.d(TAG, "[TIMING] Transcription started...")
            val transcriptionStart = System.currentTimeMillis()

            val transcribeResult = whisperContext.transcribe(
                audioFile = wavFile,
                language = language,
                translate = false
            )

            val transcriptionTimeMs = System.currentTimeMillis() - transcriptionStart
            Log.d(TAG, "[TIMING] Transcription completed in ${transcriptionTimeMs}ms (${String.format("%.2f", transcriptionTimeMs / 1000.0)}s)")

            // 8. Cleanup callbacks after transcription
            whisperContext.clearCallbacks()

            // 9. Cleanup temporary WAV file if we created it
            if (wavFile != audioFile) {
                wavFile.delete()
                Log.d(TAG, "Cleaned up temporary WAV file")
            }

            if (transcribeResult.isFailure) {
                // Clear callbacks on error
                whisperContext.clearCallbacks()
                val error = transcribeResult.exceptionOrNull()?.message ?: "Transcription failed"
                Log.e(TAG, "Transcription failed: $error")
                return@withContext ApiResult.Error("Transcription failed: $error")
            }

            val transcription = transcribeResult.getOrNull() ?: ""
            val totalTimeMs = System.currentTimeMillis() - totalStartTime

            Log.d(TAG, "✓ Transcription successful")
            Log.d(TAG, "  Result length: ${transcription.length} chars")
            Log.d(TAG, "  Result preview: ${transcription.take(100)}...")
            Log.d(TAG, "")
            Log.d(TAG, "========== TIMING SUMMARY ==========")
            Log.d(TAG, "  Model loading:    ${modelLoadTimeMs}ms")
            Log.d(TAG, "  Audio conversion: ${conversionTimeMs}ms")
            Log.d(TAG, "  Transcription:    ${transcriptionTimeMs}ms")
            Log.d(TAG, "  --------------------------------")
            Log.d(TAG, "  TOTAL TIME:       ${totalTimeMs}ms (${String.format("%.2f", totalTimeMs / 1000.0)}s)")
            Log.d(TAG, "========================================")
            Log.d(TAG, "[TIMING] Processing ended at: ${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())}")
            Log.d(TAG, "========== LOCAL WHISPER PROCESSING END ==========")

            // 10. Create processing info for transparency
            val processingInfo = ProcessingInfo(
                processingMode = "local",
                strategy = "whisper.cpp",
                transcriptionModel = model.displayName,
                postProcessingModel = null,
                translationEnabled = false,
                translationTarget = null,
                originalTranscription = null,
                voiceModeName = voiceMode.name,
                systemPrompt = voiceMode.systemPrompt,
                audioDurationSeconds = calculateAudioDuration(audioFile),
                transcriptionTokens = null, // Local processing doesn't use tokens
                postProcessingTokens = null
            )

            ApiResult.Success(transcription, processingInfo)

        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception during local processing", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            Log.d(TAG, "========== END LOCAL PROCESSING ==========")
            ApiResult.Error("Local processing failed: ${e.message}", e)
        }
    }

    /**
     * Calculate audio duration in seconds from file size
     * Approximation based on file size and bitrate
     */
    private fun calculateAudioDuration(audioFile: File): Double {
        return try {
            // For m4a at 128kbps: ~16KB per second
            val fileSizeBytes = audioFile.length()
            fileSizeBytes / 16000.0
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating audio duration", e)
            0.0
        }
    }
}
