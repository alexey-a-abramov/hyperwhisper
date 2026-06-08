package com.hyperwhisper.data.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.hyperwhisper.data.SUPPORTED_LANGUAGES

/**
 * One selectable option of an enum-typed config field.
 *
 * @param canonical the value stored in JSON/patches (usually the enum name)
 * @param displayName human-readable label shown in the diff sheet
 * @param synonyms extra spoken aliases the resolver accepts ("mic" → DICTATION)
 */
data class EnumOption(
    val canonical: String,
    val displayName: String,
    val synonyms: List<String> = emptyList(),
) {
    /** All names this option answers to, for fuzzy matching. */
    val allNames: List<String> get() = listOf(canonical, displayName) + synonyms
}

/**
 * Value-space of a [ConfigField]. Drives JSONC comments, prompt option docs,
 * patch validation/normalization, and diff-sheet display — one type, four
 * consumers.
 *
 * Resolved runtime representations:
 * - [Bool] → Boolean
 * - [IntRange] → Int
 * - [Text] → String
 * - [Enum] → String (an [EnumOption.canonical])
 * - [EnumSet] → Set<String> (canonicals)
 * - [LanguageCode] → String (ISO-639-1 code, "" = [LanguageCode.emptyMeaning])
 * - [OpaqueJson] → JsonElement (export/import only, never prompt-visible)
 */
sealed class ConfigValueType {
    object Bool : ConfigValueType()
    data class IntRange(val min: Int, val max: Int) : ConfigValueType()
    object Text : ConfigValueType()
    data class Enum(val options: List<EnumOption>) : ConfigValueType()
    data class EnumSet(val options: List<EnumOption>) : ConfigValueType()
    data class LanguageCode(val emptyMeaning: String) : ConfigValueType()
    object OpaqueJson : ConfigValueType()
}

/**
 * Descriptor for a single configurable value — the single source of truth
 * that drives (a) JSONC serialization with comments, (b) the LLM prompt's
 * option documentation, (c) patch validation + application, and (d) display
 * labels for the confirmation diff sheet.
 *
 * @param path dot-path identity, e.g. "appearance.colorScheme"
 * @param description JSONC comment text; doubles as the LLM's option docs
 * @param label short display label for the diff sheet
 * @param includeInPrompt false for fields that are exported/patchable but
 *   would bloat the voice prompt (per-provider base URLs, emoji history, …)
 * @param get reads the field's resolved value from a snapshot
 * @param set returns a copy of the snapshot with the value applied; must
 *   never touch secret material
 */
data class ConfigField(
    val path: String,
    val type: ConfigValueType,
    val description: String,
    val label: String,
    val includeInPrompt: Boolean = true,
    val get: (ConfigSnapshot) -> Any?,
    val set: (ConfigSnapshot, Any) -> ConfigSnapshot,
)

/** Outcome of resolving a raw patch value against a [ConfigValueType]. */
sealed class ResolveResult {
    data class Ok(val value: Any) : ResolveResult()
    data class Invalid(val reason: String) : ResolveResult()
}

/**
 * Normalizes raw JSON values (from LLM patches or pasted imports) onto the
 * canonical runtime representation of each [ConfigValueType]. Tolerant of
 * spoken-language variation: bool synonyms, enum display names/synonyms with
 * fuzzy fallback, language names instead of ISO codes.
 */
object ConfigValueResolver {

    private val TRUE_WORDS = setOf("true", "yes", "on", "enable", "enabled", "1")
    private val FALSE_WORDS = setOf("false", "no", "off", "disable", "disabled", "0")
    private val AUTO_WORDS = setOf("", "auto", "auto-detect", "autodetect", "automatic", "none", "default")

    fun resolve(type: ConfigValueType, raw: JsonElement): ResolveResult = when (type) {
        is ConfigValueType.Bool -> resolveBool(raw)
        is ConfigValueType.IntRange -> resolveInt(type, raw)
        is ConfigValueType.Text -> resolveText(raw)
        is ConfigValueType.Enum -> resolveEnum(type.options, raw)
        is ConfigValueType.EnumSet -> resolveEnumSet(type.options, raw)
        is ConfigValueType.LanguageCode -> resolveLanguage(raw)
        is ConfigValueType.OpaqueJson ->
            if (raw is JsonNull) ResolveResult.Invalid("Value must not be null")
            else ResolveResult.Ok(raw)
    }

    private fun resolveBool(raw: JsonElement): ResolveResult {
        if (raw.isJsonPrimitive && raw.asJsonPrimitive.isBoolean) {
            return ResolveResult.Ok(raw.asBoolean)
        }
        val word = rawAsString(raw)?.lowercase()?.trim()
            ?: return ResolveResult.Invalid("Expected true or false")
        return when (word) {
            in TRUE_WORDS -> ResolveResult.Ok(true)
            in FALSE_WORDS -> ResolveResult.Ok(false)
            else -> ResolveResult.Invalid("Expected true or false, got \"$word\"")
        }
    }

    private fun resolveInt(type: ConfigValueType.IntRange, raw: JsonElement): ResolveResult {
        val value = if (raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber) {
            raw.asInt
        } else {
            rawAsString(raw)?.trim()?.toIntOrNull()
                ?: return ResolveResult.Invalid("Expected an integer")
        }
        if (value < type.min || value > type.max) {
            return ResolveResult.Invalid("Must be between ${type.min} and ${type.max}, got $value")
        }
        return ResolveResult.Ok(value)
    }

    private fun resolveText(raw: JsonElement): ResolveResult {
        val text = rawAsString(raw) ?: return ResolveResult.Invalid("Expected a text value")
        return ResolveResult.Ok(text)
    }

    private fun resolveEnum(options: List<EnumOption>, raw: JsonElement): ResolveResult {
        val query = rawAsString(raw)?.trim()
            ?: return ResolveResult.Invalid("Expected one of: ${options.joinToString(" | ") { it.canonical }}")
        val match = FuzzyMatcher.closest(query, options) { it.allNames }
            ?: return ResolveResult.Invalid(
                "\"$query\" does not match any of: ${options.joinToString(" | ") { it.canonical }}"
            )
        return ResolveResult.Ok(match.canonical)
    }

    private fun resolveEnumSet(options: List<EnumOption>, raw: JsonElement): ResolveResult {
        val rawItems: List<String> = when {
            raw.isJsonArray -> raw.asJsonArray.mapNotNull { rawAsString(it) }
            else -> rawAsString(raw)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: return ResolveResult.Invalid("Expected an array of values")
        }
        val resolved = linkedSetOf<String>()
        for (item in rawItems) {
            val match = FuzzyMatcher.closest(item, options) { it.allNames }
                ?: return ResolveResult.Invalid(
                    "\"$item\" does not match any of: ${options.joinToString(" | ") { it.canonical }}"
                )
            resolved.add(match.canonical)
        }
        return ResolveResult.Ok(resolved as Set<String>)
    }

    private fun resolveLanguage(raw: JsonElement): ResolveResult {
        val query = rawAsString(raw)?.trim()
            ?: return ResolveResult.Invalid("Expected a language code or name")
        if (query.lowercase() in AUTO_WORDS) return ResolveResult.Ok("")

        // Exact ISO code match first
        SUPPORTED_LANGUAGES.firstOrNull { it.code.isNotEmpty() && it.code.equals(query, ignoreCase = true) }
            ?.let { return ResolveResult.Ok(it.code) }

        // Then fuzzy match on language names
        val match = FuzzyMatcher.closest(
            query,
            SUPPORTED_LANGUAGES.filter { it.code.isNotEmpty() }
        ) { listOf(it.name) }
            ?: return ResolveResult.Invalid("Unknown language: \"$query\"")
        return ResolveResult.Ok(match.code)
    }

    /**
     * Human-readable rendering of a resolved value, used for the
     * "old → new" rows in the confirmation diff sheet.
     */
    fun display(type: ConfigValueType, value: Any?): String = when (type) {
        is ConfigValueType.Bool -> if (value == true) "On" else "Off"
        is ConfigValueType.IntRange -> value?.toString() ?: "—"
        is ConfigValueType.Text -> (value as? String)?.takeIf { it.isNotEmpty() } ?: "(empty)"
        is ConfigValueType.Enum -> displayEnum(type.options, value)
        is ConfigValueType.EnumSet -> {
            val set = (value as? Set<*>).orEmpty()
            if (set.isEmpty()) "(none)"
            else set.joinToString(", ") { displayEnum(type.options, it) }
        }
        is ConfigValueType.LanguageCode -> {
            val code = value as? String ?: ""
            if (code.isEmpty()) type.emptyMeaning
            else SUPPORTED_LANGUAGES.firstOrNull { it.code == code }?.name ?: code
        }
        is ConfigValueType.OpaqueJson -> "(data)"
    }

    private fun displayEnum(options: List<EnumOption>, value: Any?): String =
        options.firstOrNull { it.canonical == value }?.displayName ?: value?.toString().orEmpty()

    /**
     * Serialize a resolved value back to a JsonElement, used by [JsoncWriter].
     */
    fun toJson(type: ConfigValueType, value: Any?): JsonElement = when (type) {
        is ConfigValueType.Bool -> JsonPrimitive(value == true)
        is ConfigValueType.IntRange -> JsonPrimitive((value as? Int) ?: 0)
        is ConfigValueType.Text -> JsonPrimitive(value as? String ?: "")
        is ConfigValueType.Enum -> JsonPrimitive(value?.toString().orEmpty())
        is ConfigValueType.LanguageCode -> JsonPrimitive(value as? String ?: "")
        is ConfigValueType.EnumSet -> JsonArray().apply {
            (value as? Set<*>).orEmpty().forEach { add(it.toString()) }
        }
        is ConfigValueType.OpaqueJson -> (value as? JsonElement) ?: JsonNull.INSTANCE
    }

    private fun rawAsString(raw: JsonElement): String? = when {
        raw.isJsonPrimitive -> raw.asJsonPrimitive.asString
        else -> null
    }
}
