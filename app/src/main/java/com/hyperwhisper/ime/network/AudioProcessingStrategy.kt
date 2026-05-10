package com.hyperwhisper.network

import com.hyperwhisper.data.ApiResult
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.telemetry.SessionTimer
import java.io.File

/**
 * Strategy Pattern for Audio Processing.
 *
 * The optional [timer] threads through phase-boundary marks for the latency
 * telemetry layer. Strategies call timer.mark("phase_name") at well-defined
 * points (request_build, network, response_parse, audio_decode,
 * whisper_inference, etc.). When `null` the calls are no-ops.
 */
interface AudioProcessingStrategy {
    suspend fun processAudio(
        audioFile: File,
        audioBase64: String,
        voiceMode: VoiceMode,
        modelId: String,
        timer: SessionTimer? = null
    ): ApiResult<String>
}
