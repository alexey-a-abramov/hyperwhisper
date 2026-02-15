package com.hyperwhisper.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingOutcomeDeciderIntegrationTest {

    @Test
    fun endToEndPriorityOrderMatchesProcessingContract() {
        assertEquals(
            ProcessingOutcome.ERROR,
            determineProcessingOutcome(
                error = "network",
                text = "text",
                hasPendingCommand = true
            )
        )

        assertEquals(
            ProcessingOutcome.PENDING_COMMAND,
            determineProcessingOutcome(
                error = null,
                text = "text",
                hasPendingCommand = true
            )
        )

        assertEquals(
            ProcessingOutcome.SUCCESS,
            determineProcessingOutcome(
                error = null,
                text = "text",
                hasPendingCommand = false
            )
        )

        assertEquals(
            ProcessingOutcome.EMPTY_TRANSCRIPTION,
            determineProcessingOutcome(
                error = null,
                text = "",
                hasPendingCommand = false
            )
        )
    }
}
