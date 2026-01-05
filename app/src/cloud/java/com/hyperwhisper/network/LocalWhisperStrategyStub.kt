package com.hyperwhisper.network

import com.hyperwhisper.data.ApiResult
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.native_whisper.WhisperProgressCallback
import com.hyperwhisper.native_whisper.WhisperSegmentCallback
import java.io.File

/**
 * Stub implementation of LocalWhisperStrategy for cloud-only builds
 * Returns error indicating local processing is not available in this build variant
 */
class LocalWhisperStrategyStub : AudioProcessingStrategy, LocalWhisperCallbacks {

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

    /**
     * No-op callback methods for cloud builds
     */
    fun setCallbacks(
        progressCallback: WhisperProgressCallback? = null,
        segmentCallback: WhisperSegmentCallback? = null
    ) {
        // No-op for cloud builds
    }

    fun clearCallbacks() {
        // No-op for cloud builds
    }
}
