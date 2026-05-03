package com.hyperwhisper.network

import com.hyperwhisper.localization.Strings

/**
 * Maps a [Throwable] to a short, user-facing message suitable for surfacing
 * from the audio-processing path. Intentionally narrow — connection-level
 * test failures use their own formatter in
 * [com.hyperwhisper.network.ConnectionTester].
 *
 * Takes [Strings] explicitly because the call site is non-Composable. Resolve
 * via `stringsFor(settingsRepository.appearanceSettings.first().uiLanguage)`.
 */
internal object ErrorMessageFormatter {
    fun friendlyMessage(t: Throwable, strings: Strings): String {
        val msg = t.message ?: ""
        return when {
            "Unable to resolve host" in msg -> strings.errorNetworkFailed
            "timeout" in msg -> strings.connectionTimeout
            "SSL" in msg || "certificate" in msg -> strings.sslError
            else -> String.format(
                strings.errorProcessingFailedFormat,
                t.javaClass.simpleName,
                msg.ifBlank { strings.errorUnknown }
            )
        }
    }
}
