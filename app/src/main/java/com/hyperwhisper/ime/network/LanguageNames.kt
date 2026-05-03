package com.hyperwhisper.network

import com.hyperwhisper.data.SUPPORTED_LANGUAGES

/**
 * ISO-639-1 code to display-name lookup, backed by [SUPPORTED_LANGUAGES].
 * Falls back to the uppercased code when the language is not in the catalog.
 */
internal object LanguageNames {
    fun displayNameFor(code: String): String {
        val language = SUPPORTED_LANGUAGES.find { it.code == code }
        return language?.name ?: code.uppercase()
    }
}
