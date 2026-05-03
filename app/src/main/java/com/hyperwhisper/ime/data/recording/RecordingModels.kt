package com.hyperwhisper.data

data class VoiceMode(
    val id: String,
    val name: String,
    val description: String = "",
    val systemPrompt: String = "",
    val model: String = "whisper-1",
    val processingMode: String = "direct",
    val isBuiltIn: Boolean = false,
    val inputLanguageHint: String = "" // Hint for input language if model supports it
)

enum class RecordingState {
    IDLE,
    RECORDING,
    RECORDING_COMPLETE_AWAITING_CONFIRMATION, // Recording finished, waiting for user confirmation (30+ sec)
    PROCESSING,
    ERROR
}

/**
 * More granular processing phases for better visual feedback
 */
enum class ProcessingPhase {
    IDLE,
    PREPARING_AUDIO,     // Converting and preparing audio file
    SENDING_TO_SERVER,   // Uploading audio to API
    WAITING_FOR_RESPONSE, // Waiting for transcription response
    RECEIVING_DATA,      // Receiving and parsing response
    COMPLETE,
    ERROR
}

/**
 * Processing stage for more detailed progress tracking
 */
enum class ProcessingStage(val displayName: String, val progressStart: Float, val progressEnd: Float) {
    PREPARING("Preparing audio...", 0.0f, 0.1f),
    CONVERTING_AUDIO("Converting audio...", 0.1f, 0.2f),
    LOADING_MODEL("Loading model...", 0.2f, 0.3f),
    TRANSCRIBING("Transcribing...", 0.3f, 0.8f),
    POST_PROCESSING("Post-processing...", 0.8f, 0.95f),
    UPLOADING("Uploading audio...", 0.1f, 0.3f),
    WAITING_API("Waiting for API...", 0.3f, 0.9f),
    FINISHING("Finishing...", 0.95f, 1.0f)
}

/**
 * Recording settings
 */
data class RecordingSettings(
    val maxRecordingDuration: Long = 180000L, // 3 minutes in milliseconds
    val warnAtSecondsRemaining: Int = 30
)
