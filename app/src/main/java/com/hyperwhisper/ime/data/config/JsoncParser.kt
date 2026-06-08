package com.hyperwhisper.data.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Tolerant JSON/JSONC reader for LLM patch output and pasted config imports.
 *
 * Handles: prose around the JSON object (extracts first `{` … last `}`),
 * `//` and `/* */` comments, and trailing commas. GSON's lenient mode accepts
 * comments but not trailing commas, so we strip both ourselves with a
 * string-aware scanner and then parse strictly.
 */
object JsoncParser {

    /** Parse [text] into a JsonObject, or null when no object can be found. */
    fun parseObject(text: String): JsonObject? {
        val candidate = extractJson(text) ?: return null
        return try {
            JsonParser.parseString(stripJsonc(candidate)).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract the outermost JSON object from text that may contain extra
     * prose (e.g. an LLM preamble): first `{` through last `}`.
     */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    /**
     * Remove `//` line comments, `/* */` block comments, and trailing commas,
     * while respecting string literals (including escape sequences).
     */
    fun stripJsonc(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    out.append(c)
                    when (c) {
                        '\\' -> { // copy escaped char verbatim
                            if (i + 1 < text.length) {
                                out.append(text[i + 1])
                                i++
                            }
                        }
                        '"' -> inString = false
                    }
                }
                c == '"' -> {
                    inString = true
                    out.append(c)
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    while (i < text.length && text[i] != '\n') i++
                    continue // keep the newline for line counting
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i++ // skip the '/'
                }
                else -> out.append(c)
            }
            i++
        }
        return removeTrailingCommas(out.toString())
    }

    /** Remove commas directly preceding `}` or `]` (ignoring whitespace), string-aware. */
    private fun removeTrailingCommas(text: String): String {
        val out = StringBuilder(text.length)
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < text.length) {
                    out.append(text[i + 1])
                    i++
                } else if (c == '"') {
                    inString = false
                }
            } else if (c == '"') {
                inString = true
                out.append(c)
            } else if (c == ',') {
                // Look ahead past whitespace: drop the comma if a closer follows.
                var j = i + 1
                while (j < text.length && text[j].isWhitespace()) j++
                if (j < text.length && (text[j] == '}' || text[j] == ']')) {
                    // skip this comma
                } else {
                    out.append(c)
                }
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
