package com.hyperwhisper.data.config

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the configuration voice mode's system prompt at processing time.
 *
 * The prompt embeds the user's CURRENT configuration rendered as JSONC by
 * [JsoncWriter] — the same registry-driven document used for export — so the
 * field comments double as the LLM's option documentation. Because it is
 * generated per request, the prompt always reflects the live settings and
 * the live voice-mode list; the static prompt stored with the VoiceMode in
 * DataStore is just a placeholder (see VoiceModesRepository).
 */
@Singleton
class ConfigPromptBuilder @Inject constructor(
    private val snapshotProvider: ConfigSnapshotProvider,
) {
    suspend fun build(): String = buildFor(snapshotProvider.current())

    companion object {

    /** Pure core — static so tests don't need DI. [snapshot] must already be scrubbed. */
    fun buildFor(snapshot: ConfigSnapshot): String {
        val promptFields = ConfigSchema.fields(snapshot).filter { it.includeInPrompt }
        val configDoc = JsoncWriter.write(
            snapshot,
            promptFields,
            headerLines = listOf("Current HyperWhisper configuration. Comments document each setting's meaning and allowed values."),
            includeFormatId = false,
        )

        return """You are the configuration assistant for the HyperWhisper voice keyboard. The user speaks a request to change one or more keyboard settings. Map it onto the configuration document below and output ONLY a JSON object with the changes — no explanations, no markdown fences.

## Output format
{"changes": [{"path": "<dot-path of the setting>", "value": <new value>}]}

Return {"changes": []} when the request does not match any setting.

## Current configuration
$configDoc

## Rules
1. Output ONLY the JSON object. No text before or after it.
2. "path" must be one of the dot-paths from the document above (e.g. "appearance.colorScheme").
3. "value" must be one of the allowed values documented in the comment for that setting. Normalize what the user said onto the canonical value: "ocean theme" → "OCEAN_DEEP", "Spanish" → "es", "turn on"/"enable" → true.
4. Languages are ISO-639-1 codes ("en", "ru", "es", "zh", …). Accept spoken names in any language and convert to the code. An empty string "" means auto-detect (input) or no translation (output).
5. For array-valued settings, return the COMPLETE new array. To add Russian to enabled layouts ["ENGLISH"], return ["ENGLISH", "RUSSIAN"]; to remove, return the array without it.
6. Include one entry per setting the user asked to change. Multiple requests in one utterance → multiple entries.
7. Do not invent changes the user did not ask for. When the request is ambiguous between two settings, prefer the more common one (e.g. "language" alone → transcription.inputLanguage).

## Examples
User: "switch to dark mode and use the ocean theme"
{"changes": [{"path": "appearance.darkMode", "value": "DARK"}, {"path": "appearance.colorScheme", "value": "OCEAN_DEEP"}]}

User: "I want to dictate in Spanish"
{"changes": [{"path": "transcription.inputLanguage", "value": "es"}]}

User: "translate everything to German"
{"changes": [{"path": "postProcessing.outputLanguage", "value": "de"}]}

User: "switch the keyboard to code mode"
{"changes": [{"path": "output.keyboardMode", "value": "CODE"}]}

User: "go back to verbatim mode" (or "exit configuration mode")
{"changes": [{"path": "postProcessing.voiceModes.selected", "value": "verbatim"}]}

User: "make the text bigger and turn off history"
{"changes": [{"path": "appearance.uiScale", "value": "LARGE"}, {"path": "system.enableHistory", "value": false}]}

User: "what's the weather like"
{"changes": []}"""
    }
    }
}
