package com.hyperwhisper.ui

internal enum class ProcessingOutcome {
    ERROR,
    PENDING_COMMAND,
    SUCCESS,
    EMPTY_TRANSCRIPTION
}

internal fun determineProcessingOutcome(
    error: String?,
    text: String,
    hasPendingCommand: Boolean
): ProcessingOutcome {
    return when {
        error != null -> ProcessingOutcome.ERROR
        hasPendingCommand -> ProcessingOutcome.PENDING_COMMAND
        text.isNotBlank() -> ProcessingOutcome.SUCCESS
        else -> ProcessingOutcome.EMPTY_TRANSCRIPTION
    }
}
