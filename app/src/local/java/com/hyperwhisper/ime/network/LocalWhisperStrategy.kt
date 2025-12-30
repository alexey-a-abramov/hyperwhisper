package com.hyperwhisper.network

import android.util.Log
import com.hyperwhisper.data.*
import com.hyperwhisper.native_whisper.AudioConverter
import com.hyperwhisper.native_whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
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
) : AudioProcessingStrategy {

    companion object {
        private const val TAG = "LocalWhisperStrategy"
    }

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String, // Not used for local processing
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "========== LOCAL WHISPER PROCESSING ==========")
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
            if (!whisperContext.isModelLoaded()) {
                Log.d(TAG, "Loading model: ${modelFile.absolutePath}")
                val loadResult = whisperContext.loadModel(modelFile)
                if (loadResult.isFailure) {
                    val error = loadResult.exceptionOrNull()?.message ?: "Failed to load model"
                    Log.e(TAG, "Model loading failed: $error")
                    return@withContext ApiResult.Error("Failed to load model: $error")
                }
                Log.d(TAG, "Model loaded successfully")
            } else {
                Log.d(TAG, "Model already loaded")
            }

            // 4. Convert M4A to WAV if needed
            val wavFile = if (audioFile.extension.lowercase() == "m4a") {
                Log.d(TAG, "Converting M4A to WAV...")
                val convertResult = audioConverter.convertM4AToWav(audioFile, audioFile.parentFile!!)
                if (convertResult.isFailure) {
                    val error = convertResult.exceptionOrNull()?.message ?: "Conversion failed"
                    Log.e(TAG, "Audio conversion failed: $error")
                    return@withContext ApiResult.Error("Audio conversion failed: $error")
                }
                val wav = convertResult.getOrNull()!!
                Log.d(TAG, "Conversion successful: ${wav.name} (${wav.length()} bytes)")
                wav
            } else {
                Log.d(TAG, "Audio is already in WAV format")
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

            // 6. Transcribe with whisper.cpp
            Log.d(TAG, "Starting transcription...")
            val startTime = System.currentTimeMillis()

            val transcribeResult = whisperContext.transcribe(
                audioFile = wavFile,
                language = language,
                translate = false
            )

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Transcription completed in ${elapsedTime}ms")

            // 7. Cleanup temporary WAV file if we created it
            if (wavFile != audioFile) {
                wavFile.delete()
                Log.d(TAG, "Cleaned up temporary WAV file")
            }

            if (transcribeResult.isFailure) {
                val error = transcribeResult.exceptionOrNull()?.message ?: "Transcription failed"
                Log.e(TAG, "Transcription failed: $error")
                return@withContext ApiResult.Error("Transcription failed: $error")
            }

            val transcription = transcribeResult.getOrNull() ?: ""
            Log.d(TAG, "✓ Transcription successful")
            Log.d(TAG, "  Result length: ${transcription.length} chars")
            Log.d(TAG, "  Result preview: ${transcription.take(100)}...")
            Log.d(TAG, "  Processing time: ${elapsedTime}ms (${String.format("%.2f", elapsedTime / 1000.0)}s)")
            Log.d(TAG, "========== END LOCAL PROCESSING ==========")

            // 8. Create processing info for transparency
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
