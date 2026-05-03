package com.hyperwhisper.network

import com.hyperwhisper.data.ApiResult
import com.hyperwhisper.data.VoiceMode
import java.io.File

/**
 * Strategy Pattern for Audio Processing
 */
interface AudioProcessingStrategy {
    suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String
    ): ApiResult<String>
}
