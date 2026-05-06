package com.hyperwhisper.data

/**
 * Coarse classification of API/network errors used to drive the
 * suggested-action button in [com.hyperwhisper.ui.overlays.ErrorOverlay].
 *
 * Pure presentation taxonomy — error strings flow through unchanged; the
 * overlay calls [classifyErrorMessage] at display time. No auto-retry or
 * fallback routing is wired off these (per the explicit decision in the
 * keyboard polish roadmap §D); the kind only chooses which manual action
 * to suggest the user take.
 */
enum class ErrorKind {
    NETWORK,         // DNS / TLS / unreachable host — connectivity failure
    AUTH,            // 401 / 403 / "API key" / "Unauthorized"
    MODEL_NOT_FOUND, // 404 / "model not found" / "unknown_model"
    RATE_LIMITED,    // 429 / "rate limit" / "quota"
    TIMEOUT,         // request timeout / read timeout
    PROVIDER_DOWN,   // 5xx / "service unavailable" / "internal_error"
    UNKNOWN,
}

/**
 * Best-effort classification from an existing user-facing error string. The
 * input is expected to be either the raw exception message or one of the
 * already-prettified strings from the localised `Strings` table — both
 * carry the same diagnostic signals (HTTP code, "timeout", "rate limit",
 * etc.) so a single matcher covers them.
 */
fun classifyErrorMessage(message: String?): ErrorKind {
    if (message.isNullOrBlank()) return ErrorKind.UNKNOWN
    val lower = message.lowercase()
    return when {
        // HTTP codes are the most reliable signal — check first.
        "401" in lower || "unauthorized" in lower ||
            "api key" in lower || "authentication" in lower -> ErrorKind.AUTH
        "403" in lower || "forbidden" in lower -> ErrorKind.AUTH
        "404" in lower || "not found" in lower ||
            "unknown_model" in lower || "model not found" in lower -> ErrorKind.MODEL_NOT_FOUND
        "429" in lower || "rate limit" in lower || "quota" in lower ||
            "too many requests" in lower -> ErrorKind.RATE_LIMITED
        "timeout" in lower || "timed out" in lower -> ErrorKind.TIMEOUT
        // 5xx + provider-side wording
        Regex("\\b5\\d\\d\\b").containsMatchIn(lower) ||
            "service unavailable" in lower ||
            "internal error" in lower ||
            "bad gateway" in lower -> ErrorKind.PROVIDER_DOWN
        // Network-layer wording — keep last so it loses to the explicit
        // codes above (a 401 over a flaky network should suggest auth, not
        // network).
        "ssl" in lower || "certificate" in lower ||
            "unable to resolve" in lower || "host" in lower ||
            "connection refused" in lower || "network" in lower -> ErrorKind.NETWORK
        else -> ErrorKind.UNKNOWN
    }
}
