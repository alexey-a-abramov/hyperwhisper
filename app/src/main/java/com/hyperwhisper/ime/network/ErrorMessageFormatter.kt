package com.hyperwhisper.network

/**
 * Maps a [Throwable] to a short, user-facing message suitable for surfacing
 * from the audio-processing path. Intentionally narrow — connection-level
 * test failures use their own formatter in
 * [com.hyperwhisper.network.ConnectionTester].
 */
internal object ErrorMessageFormatter {
    fun friendlyMessage(t: Throwable): String {
        val msg = t.message ?: ""
        return when {
            "Unable to resolve host" in msg -> "Cannot reach server — check internet connection."
            "timeout" in msg -> "Request timed out — server not responding."
            "SSL" in msg || "certificate" in msg -> "SSL/Certificate error — check HTTPS configuration."
            else -> "Processing failed: ${t.javaClass.simpleName}: ${msg.ifBlank { "unknown error" }}"
        }
    }
}
