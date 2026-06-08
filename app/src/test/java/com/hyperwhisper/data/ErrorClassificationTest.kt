package com.hyperwhisper.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [classifyErrorMessage] — the taxonomy that drives the
 * suggested-action button on the error overlay. Covers each provider/API
 * failure "mode" and the precedence rules between overlapping signals.
 */
class ErrorClassificationTest {

    @Test
    fun authErrors() {
        assertEquals(ErrorKind.AUTH, classifyErrorMessage("HTTP 401 Unauthorized"))
        assertEquals(ErrorKind.AUTH, classifyErrorMessage("Invalid API key provided"))
        assertEquals(ErrorKind.AUTH, classifyErrorMessage("403 Forbidden"))
        assertEquals(ErrorKind.AUTH, classifyErrorMessage("authentication failed"))
    }

    @Test
    fun modelNotFoundErrors() {
        assertEquals(ErrorKind.MODEL_NOT_FOUND, classifyErrorMessage("HTTP 404"))
        assertEquals(ErrorKind.MODEL_NOT_FOUND, classifyErrorMessage("model not found"))
        assertEquals(ErrorKind.MODEL_NOT_FOUND, classifyErrorMessage("unknown_model: gpt-9"))
    }

    @Test
    fun rateLimitErrors() {
        assertEquals(ErrorKind.RATE_LIMITED, classifyErrorMessage("429 Too Many Requests"))
        assertEquals(ErrorKind.RATE_LIMITED, classifyErrorMessage("You exceeded your quota"))
        assertEquals(ErrorKind.RATE_LIMITED, classifyErrorMessage("rate limit reached"))
    }

    @Test
    fun timeoutErrors() {
        assertEquals(ErrorKind.TIMEOUT, classifyErrorMessage("Read timed out"))
        assertEquals(ErrorKind.TIMEOUT, classifyErrorMessage("connection timeout"))
    }

    @Test
    fun providerDownErrors() {
        assertEquals(ErrorKind.PROVIDER_DOWN, classifyErrorMessage("HTTP 503 Service Unavailable"))
        assertEquals(ErrorKind.PROVIDER_DOWN, classifyErrorMessage("502 Bad Gateway"))
        assertEquals(ErrorKind.PROVIDER_DOWN, classifyErrorMessage("internal error, please retry"))
    }

    @Test
    fun networkErrors() {
        assertEquals(ErrorKind.NETWORK, classifyErrorMessage("Unable to resolve host"))
        assertEquals(ErrorKind.NETWORK, classifyErrorMessage("SSL handshake failed"))
        assertEquals(ErrorKind.NETWORK, classifyErrorMessage("Connection refused"))
    }

    @Test
    fun nullOrBlank_isUnknown() {
        assertEquals(ErrorKind.UNKNOWN, classifyErrorMessage(null))
        assertEquals(ErrorKind.UNKNOWN, classifyErrorMessage(""))
        assertEquals(ErrorKind.UNKNOWN, classifyErrorMessage("   "))
    }

    @Test
    fun unrecognized_isUnknown() {
        assertEquals(ErrorKind.UNKNOWN, classifyErrorMessage("something weird happened"))
    }

    @Test
    fun httpCodeWinsOverNetworkWording() {
        // A 401 received over a flaky connection should suggest AUTH, not
        // NETWORK — the explicit code is checked before network wording.
        assertEquals(ErrorKind.AUTH, classifyErrorMessage("401 unauthorized on host api.openai.com"))
    }
}
