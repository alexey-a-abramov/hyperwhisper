package com.hyperwhisper.network

import com.hyperwhisper.data.ApiResult
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.ime.network.AudioProcessingStrategy
import java.io.File

/**
 * Stub implementation of LocalWhisperStrategy for cloud-only builds
 * Returns error indicating local processing is not available in this build variant
 */
class LocalWhisperStrategyStub : AudioProcessingStrategy {

    override suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String> {
        return ApiResult.Error(
            "Local processing is not available in this build variant. " +
            "Please use the 'local' flavor to enable on-device processing with whisper.cpp, " +
            "or select a cloud-based API provider (OpenAI, Groq, etc.)."
        )
    }
}
