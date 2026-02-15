package com.hyperwhisper.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingOutcomeDeciderUnitTest {

    @Test
    fun returnsErrorWhenErrorIsPresent() {
        val outcome = determineProcessingOutcome(
            error = "API error",
            text = "transcribed",
            hasPendingCommand = true
        )

        assertEquals(ProcessingOutcome.ERROR, outcome)
    }

    @Test
    fun returnsPendingCommandWhenNoErrorAndPendingExists() {
        val outcome = determineProcessingOutcome(
            error = null,
            text = "transcribed",
            hasPendingCommand = true
        )

        assertEquals(ProcessingOutcome.PENDING_COMMAND, outcome)
    }

    @Test
    fun returnsSuccessWhenTextIsNonBlank() {
        val outcome = determineProcessingOutcome(
            error = null,
            text = "hello world",
            hasPendingCommand = false
        )

        assertEquals(ProcessingOutcome.SUCCESS, outcome)
    }

    @Test
    fun returnsEmptyTranscriptionWhenTextIsBlankAndNoError() {
        val outcome = determineProcessingOutcome(
            error = null,
            text = "   ",
            hasPendingCommand = false
        )

        assertEquals(ProcessingOutcome.EMPTY_TRANSCRIPTION, outcome)
    }
}
