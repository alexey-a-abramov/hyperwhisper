package com.hyperwhisper.security

import androidx.fragment.app.FragmentActivity

/**
 * UI-layer gate that surfaces a system biometric/device-credential prompt before
 * a privileged action (revealing or exporting a stored API key) is permitted.
 *
 * The gate is intentionally separate from [SecretsRepository] so that silent
 * decryption for HTTP requests doesn't trigger an auth prompt — only explicit
 * user-facing reveal/export flows go through here.
 */
interface BiometricGate {
    fun availability(): Availability

    /**
     * Shows the system prompt and reports the outcome on the main thread.
     * The prompt allows BIOMETRIC_STRONG and DEVICE_CREDENTIAL (PIN/pattern/password)
     * so the user can fall back to their device lock if no biometric is enrolled.
     */
    fun request(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String? = null,
        onResult: (Result) -> Unit,
    )

    enum class Availability {
        /** Ready — biometric and/or device credential is enrolled. */
        READY,

        /** Hardware capable but no PIN/biometric enrolled — user needs Settings. */
        NOT_ENROLLED,

        /** No suitable hardware or otherwise unavailable. */
        UNAVAILABLE,
    }

    sealed class Result {
        object Success : Result()
        object Cancelled : Result()
        data class Failed(val message: String) : Result()
    }
}
