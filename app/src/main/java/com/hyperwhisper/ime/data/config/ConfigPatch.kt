package com.hyperwhisper.data.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Where a pending patch came from — affects copy in the confirmation UI. */
enum class PatchSource { VOICE, IMPORT }

/** A single validated, normalized setting change awaiting confirmation. */
data class ResolvedChange(
    val field: ConfigField,
    val oldValue: Any?,
    val newValue: Any,
    val oldDisplay: String,
    val newDisplay: String,
)

/** A change that could not be validated; surfaced in the confirmation sheet. */
data class ChangeError(
    val path: String,
    val rawValue: String,
    val reason: String,
)

/**
 * The unit the confirmation diff sheet operates on. Nothing in a pending
 * patch has been persisted; [ConfigPatchApplier.apply] runs only after the
 * user confirms.
 */
data class PendingConfigPatch(
    val valid: List<ResolvedChange>,
    val errors: List<ChangeError>,
    val source: PatchSource,
) {
    val isEmpty: Boolean get() = valid.isEmpty() && errors.isEmpty()
}

/**
 * Parses raw LLM output / pasted import documents into a [PendingConfigPatch],
 * validating every change against the [ConfigSchema] registry.
 */
object ConfigPatchParser {

    /**
     * Parse configuration-mode LLM output: `{"changes": [{"path": ..., "value": ...}]}`.
     * Tolerates prose around the JSON, comments, trailing commas, and a few
     * shape variations (a bare `[...]` array, or `"setting"` instead of `"path"`).
     * Returns null when no JSON object/array can be found at all.
     */
    fun parseLlmOutput(raw: String, snapshot: ConfigSnapshot): PendingConfigPatch? {
        val obj = JsoncParser.parseObject(raw)
        val changesArray: List<JsonElement> = when {
            obj?.get("changes")?.isJsonArray == true ->
                obj.getAsJsonArray("changes").toList()
            // Single change object without the wrapper: {"path": ..., "value": ...}
            obj != null && obj.has("path") && obj.has("value") ->
                listOf(obj)
            else -> {
                // Bare array fallback: [{"path": ..., "value": ...}, ...]
                val start = raw.indexOf('[')
                val end = raw.lastIndexOf(']')
                val bareArray = if (start in 0 until end) {
                    try {
                        com.google.gson.JsonParser
                            .parseString(JsoncParser.stripJsonc(raw.substring(start, end + 1)))
                            .takeIf { it.isJsonArray }?.asJsonArray?.toList()
                    } catch (e: Exception) {
                        null
                    }
                } else null
                when {
                    bareArray != null -> bareArray
                    // An object without "changes" → empty patch (LLM answered
                    // but found nothing actionable); no JSON at all → null.
                    obj != null -> emptyList()
                    else -> return null
                }
            }
        }

        val byPath = ConfigSchema.byPath(snapshot)
        val valid = mutableListOf<ResolvedChange>()
        val errors = mutableListOf<ChangeError>()

        for (element in changesArray) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val path = stringMember(obj, "path") ?: stringMember(obj, "setting") ?: ""
            val rawValue = obj.get("value")
            if (path.isEmpty() || rawValue == null) {
                errors.add(ChangeError(path.ifEmpty { "?" }, rawValue?.toString() ?: "", "Malformed change entry"))
                continue
            }
            val field = byPath[path.lowercase().trim()]
            if (field == null) {
                errors.add(ChangeError(path, rawValue.toString(), "Unknown setting"))
                continue
            }
            when (val result = ConfigValueResolver.resolve(field.type, rawValue)) {
                is ResolveResult.Invalid ->
                    errors.add(ChangeError(field.path, rawValue.toString(), result.reason))
                is ResolveResult.Ok -> {
                    val change = toChange(field, snapshot, result.value)
                    if (change != null) valid.add(change) // drop no-ops
                }
            }
        }

        return PendingConfigPatch(valid, errors, PatchSource.VOICE)
    }

    /**
     * Parse a full (possibly hand-edited) JSONC config document and diff it
     * against the current snapshot. Only fields present in the document are
     * considered; everything else stays untouched.
     */
    fun parseImport(raw: String, snapshot: ConfigSnapshot): PendingConfigPatch? {
        val doc = JsoncParser.parseObject(raw) ?: return null

        val valid = mutableListOf<ResolvedChange>()
        val errors = mutableListOf<ChangeError>()

        for (field in ConfigSchema.fields(snapshot)) {
            val rawValue = navigate(doc, field.path.split('.')) ?: continue
            when (val result = ConfigValueResolver.resolve(field.type, rawValue)) {
                is ResolveResult.Invalid ->
                    errors.add(ChangeError(field.path, rawValue.toString(), result.reason))
                is ResolveResult.Ok -> {
                    val change = toChange(field, snapshot, result.value)
                    if (change != null) valid.add(change)
                }
            }
        }

        return PendingConfigPatch(valid, errors, PatchSource.IMPORT)
    }

    /** Build a [ResolvedChange], or null when the value equals the current one. */
    private fun toChange(field: ConfigField, snapshot: ConfigSnapshot, newValue: Any): ResolvedChange? {
        val oldValue = field.get(snapshot)
        val unchanged = when {
            field.type is ConfigValueType.OpaqueJson ->
                ConfigSchema.jsonEquals(oldValue as? JsonElement, newValue as? JsonElement)
            else -> oldValue == newValue
        }
        if (unchanged) return null
        return ResolvedChange(
            field = field,
            oldValue = oldValue,
            newValue = newValue,
            oldDisplay = ConfigValueResolver.display(field.type, oldValue),
            newDisplay = ConfigValueResolver.display(field.type, newValue),
        )
    }

    private fun navigate(obj: JsonObject, segments: List<String>): JsonElement? {
        var current: JsonElement = obj
        for (segment in segments) {
            if (!current.isJsonObject) return null
            current = current.asJsonObject.get(segment) ?: return null
        }
        return current
    }

    private fun stringMember(obj: JsonObject, name: String): String? =
        obj.get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
