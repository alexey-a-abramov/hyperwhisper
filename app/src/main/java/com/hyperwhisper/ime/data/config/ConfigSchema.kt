package com.hyperwhisper.data.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.data.DarkModePreference
import com.hyperwhisper.data.FontFamilyOption
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.KeyboardLayout
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.LlmProviderConfig
import com.hyperwhisper.data.ProviderConfig
import com.hyperwhisper.data.UIScaleOption
import com.hyperwhisper.localization.AppLanguage

/**
 * The single source of truth for every voice-/import-configurable setting.
 *
 * Each [ConfigField] in [fields] drives:
 *  1. JSONC export with option-documenting comments ([JsoncWriter])
 *  2. the configuration-mode LLM prompt ([ConfigPromptBuilder])
 *  3. patch validation + application ([ConfigPatchParser]/[ConfigPatchApplier])
 *  4. display labels in the confirmation diff sheet
 *
 * Secret material (API keys) is intentionally absent: there are no key paths,
 * and snapshots are scrubbed at construction anyway (see [ConfigSnapshotProvider]).
 */
object ConfigSchema {

    private val gson = Gson()

    /**
     * The registry is snapshot-aware because voice-mode options (ids/names)
     * are user-editable and must reflect the live list.
     */
    fun fields(snapshot: ConfigSnapshot): List<ConfigField> =
        transcriptionFields() +
            llmFields() +
            localModelFields() +
            appearanceFields() +
            voiceModeFields(snapshot)

    /** Fields addressable by patch path (everything). */
    fun byPath(snapshot: ConfigSnapshot): Map<String, ConfigField> =
        fields(snapshot).associateBy { it.path.lowercase() }

    // ------------------------------------------------------------------
    // transcription.*
    // ------------------------------------------------------------------

    private fun transcriptionFields(): List<ConfigField> {
        val providerOptions = ApiProvider.entries.map { EnumOption(it.name, it.displayName) }
        val modelDocs = ApiProvider.entries.joinToString("; ") {
            "${it.name}: ${it.defaultModels.joinToString("|")}"
        }

        val base = listOf(
            ConfigField(
                path = "transcription.provider",
                type = ConfigValueType.Enum(providerOptions),
                description = "Speech-to-text (ASR) provider used for transcription",
                label = "Transcription provider",
                get = { it.api.provider.name },
                set = { s, v -> s.copy(api = s.api.copy(provider = ApiProvider.valueOf(v as String))) },
            ),
            ConfigField(
                path = "transcription.modelId",
                type = ConfigValueType.Text,
                description = "Model ID for the ASR provider. Typical models per provider: $modelDocs",
                label = "Transcription model",
                get = { it.api.modelId },
                set = { s, v -> s.copy(api = s.api.copy(modelId = v as String)) },
            ),
            ConfigField(
                path = "transcription.inputLanguage",
                type = ConfigValueType.LanguageCode(emptyMeaning = "Auto-detect"),
                description = "Speech recognition language as ISO-639-1 code (en, ru, es, …); \"\" = auto-detect. Accepts language names",
                label = "Input language",
                get = { it.api.inputLanguage },
                set = { s, v -> s.copy(api = s.api.copy(inputLanguage = v as String)) },
            ),
            ConfigField(
                path = "transcription.outputLanguage",
                type = ConfigValueType.LanguageCode(emptyMeaning = "No translation"),
                description = "Translation target language as ISO-639-1 code; \"\" = keep original language (no translation)",
                label = "Output language",
                get = { it.api.outputLanguage },
                set = { s, v -> s.copy(api = s.api.copy(outputLanguage = v as String)) },
            ),
        )

        // Per-provider endpoint overrides — patchable + exported, hidden from
        // the voice prompt to keep it compact.
        val perProvider = ApiProvider.entries.flatMap { provider ->
            listOf(
                ConfigField(
                    path = "transcription.providers.${provider.name}.baseUrl",
                    type = ConfigValueType.Text,
                    description = "Custom endpoint for ${provider.displayName}; \"\" = default (${provider.defaultEndpoint})",
                    label = "${provider.displayName} base URL",
                    includeInPrompt = false,
                    get = { it.api.providerConfigs[provider]?.customBaseUrl ?: "" },
                    set = { s, v ->
                        val cfg = s.api.providerConfigs[provider] ?: ProviderConfig(requiresAuth = provider.requiresAuth)
                        s.copy(
                            api = s.api.copy(
                                providerConfigs = s.api.providerConfigs + (provider to cfg.copy(customBaseUrl = v as String))
                            )
                        )
                    },
                ),
                ConfigField(
                    path = "transcription.providers.${provider.name}.requiresAuth",
                    type = ConfigValueType.Bool,
                    description = "Whether ${provider.displayName} requests send an API key",
                    label = "${provider.displayName} requires auth",
                    includeInPrompt = false,
                    get = { it.api.providerConfigs[provider]?.requiresAuth ?: provider.requiresAuth },
                    set = { s, v ->
                        val cfg = s.api.providerConfigs[provider] ?: ProviderConfig(requiresAuth = provider.requiresAuth)
                        s.copy(
                            api = s.api.copy(
                                providerConfigs = s.api.providerConfigs + (provider to cfg.copy(requiresAuth = v as Boolean))
                            )
                        )
                    },
                ),
            )
        }

        return base + perProvider
    }

    // ------------------------------------------------------------------
    // llm.*
    // ------------------------------------------------------------------

    private fun llmFields(): List<ConfigField> {
        val providerOptions = LlmProvider.entries.map { EnumOption(it.name, it.displayName) }
        val modelDocs = LlmProvider.entries
            .filter { it != LlmProvider.NONE }
            .joinToString("; ") { "${it.name}: ${it.defaultModels.joinToString("|")}" }

        val base = listOf(
            ConfigField(
                path = "llm.provider",
                type = ConfigValueType.Enum(providerOptions),
                description = "LLM provider for post-processing transcribed text (grammar fixes, tone transforms, this configuration mode). NONE disables post-processing",
                label = "LLM provider",
                get = { it.api.llmConfig.provider.name },
                set = { s, v ->
                    s.copy(api = s.api.copy(llmConfig = s.api.llmConfig.copy(provider = LlmProvider.valueOf(v as String))))
                },
            ),
            ConfigField(
                path = "llm.modelId",
                type = ConfigValueType.Text,
                description = "Model ID for the LLM provider. Typical models per provider: $modelDocs",
                label = "LLM model",
                get = { it.api.llmConfig.modelId },
                set = { s, v -> s.copy(api = s.api.copy(llmConfig = s.api.llmConfig.copy(modelId = v as String))) },
            ),
        )

        val perProvider = LlmProvider.entries.filter { it != LlmProvider.NONE }.flatMap { provider ->
            listOf(
                ConfigField(
                    path = "llm.providers.${provider.name}.baseUrl",
                    type = ConfigValueType.Text,
                    description = "Custom endpoint for ${provider.displayName}; \"\" = default (${provider.defaultEndpoint})",
                    label = "${provider.displayName} base URL",
                    includeInPrompt = false,
                    get = { it.api.llmConfig.providerConfigs[provider]?.customBaseUrl ?: "" },
                    set = { s, v ->
                        val cfg = s.api.llmConfig.providerConfigs[provider] ?: LlmProviderConfig()
                        s.copy(
                            api = s.api.copy(
                                llmConfig = s.api.llmConfig.copy(
                                    providerConfigs = s.api.llmConfig.providerConfigs +
                                        (provider to cfg.copy(customBaseUrl = v as String))
                                )
                            )
                        )
                    },
                ),
                ConfigField(
                    path = "llm.providers.${provider.name}.requiresAuth",
                    type = ConfigValueType.Bool,
                    description = "Whether ${provider.displayName} post-processing requests send an API key",
                    label = "${provider.displayName} requires auth",
                    includeInPrompt = false,
                    get = { it.api.llmConfig.providerConfigs[provider]?.requiresAuth ?: provider.requiresAuth },
                    set = { s, v ->
                        val cfg = s.api.llmConfig.providerConfigs[provider] ?: LlmProviderConfig()
                        s.copy(
                            api = s.api.copy(
                                llmConfig = s.api.llmConfig.copy(
                                    providerConfigs = s.api.llmConfig.providerConfigs +
                                        (provider to cfg.copy(requiresAuth = v as Boolean))
                                )
                            )
                        )
                    },
                ),
            )
        }

        return base + perProvider
    }

    // ------------------------------------------------------------------
    // localModels.*
    // ------------------------------------------------------------------

    private fun localModelFields(): List<ConfigField> = listOf(
        ConfigField(
            path = "localModels.useLocalWhisper",
            type = ConfigValueType.Bool,
            description = "Transcribe on-device with a local Whisper model instead of a cloud provider",
            label = "Local Whisper",
            get = { it.api.localModelSettings.useLocalWhisper },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(useLocalWhisper = v as Boolean)))
            },
        ),
        ConfigField(
            path = "localModels.useLocalGemma",
            type = ConfigValueType.Bool,
            description = "Post-process on-device with a local LLM (Gemma .task or llama.cpp .gguf) instead of a cloud LLM",
            label = "Local LLM",
            get = { it.api.localModelSettings.useLocalGemma },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(useLocalGemma = v as Boolean)))
            },
        ),
        ConfigField(
            path = "localModels.threads",
            type = ConfigValueType.IntRange(1, 16),
            description = "CPU threads for on-device inference",
            label = "Inference threads",
            get = { it.api.localModelSettings.threads },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(threads = v as Int)))
            },
        ),
        ConfigField(
            path = "localModels.autoDiscover",
            type = ConfigValueType.Bool,
            description = "Automatically discover model files on device storage",
            label = "Auto-discover models",
            get = { it.api.localModelSettings.autoDiscover },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(autoDiscover = v as Boolean)))
            },
        ),
        ConfigField(
            path = "localModels.whisperModelPath",
            type = ConfigValueType.Text,
            description = "File path of the local Whisper model (.bin)",
            label = "Whisper model path",
            includeInPrompt = false,
            get = { it.api.localModelSettings.whisperModelPath },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(whisperModelPath = v as String)))
            },
        ),
        ConfigField(
            path = "localModels.llmModelPath",
            type = ConfigValueType.Text,
            description = "File path of the local LLM model (.task, .litertlm or .gguf)",
            label = "Local LLM model path",
            includeInPrompt = false,
            get = { it.api.localModelSettings.gemmaModelPath },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(gemmaModelPath = v as String)))
            },
        ),
    )

    // ------------------------------------------------------------------
    // appearance.*
    // ------------------------------------------------------------------

    /** Keyboard mode options with the spoken synonyms the old voice-command
     *  flow accepted (VoiceCommand.getKeyboardMode), legacy enum values excluded. */
    private val keyboardModeOptions: List<EnumOption> = listOf(
        EnumOption("DICTATION", "Voice", listOf("voice", "dictation", "mic", "voice input")),
        EnumOption("QWERTY", "Text", listOf("text", "abc", "qwerty", "letters", "alphabet", "typing")),
        EnumOption("CODE", "Code", listOf("code", "coding", "programmer", "numpad", "symbols", "system keys")),
        EnumOption("EMOJI", "Emoji", listOf("emoji", "emojis", "emoticons")),
        EnumOption("AGENT_CLAUDE_CODE", "Claude Code", listOf("claude code", "claude")),
        EnumOption("AGENT_OPENCODE", "OpenCode", listOf("opencode")),
        EnumOption("AGENT_GEMINI", "Gemini CLI", listOf("gemini", "gemini cli")),
        EnumOption("AGENT_CODEX", "Codex CLI", listOf("codex", "codex cli")),
        EnumOption("AGENT_MACROS", "Text Snippets", listOf("macros", "snippets", "phrases")),
        EnumOption("EXPERIMENTAL_TERMINAL", "Terminal", listOf("terminal", "termux")),
    )

    private val keyboardLayoutOptions: List<EnumOption> = KeyboardLayout.entries.map {
        EnumOption(it.name, it.displayName, listOf(it.code, it.nativeName))
    }

    private val agentKeyboardOptions: List<EnumOption> =
        keyboardModeOptions.filter { KeyboardInputMode.valueOf(it.canonical).isAgent }

    private fun appearanceFields(): List<ConfigField> = listOf(
        ConfigField(
            path = "appearance.colorScheme",
            type = ConfigValueType.Enum(ColorSchemeOption.entries.map { EnumOption(it.name, it.displayName) }),
            description = "Color theme of the keyboard and app",
            label = "Color scheme",
            get = { it.appearance.colorScheme.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(colorScheme = ColorSchemeOption.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.useDynamicColor",
            type = ConfigValueType.Bool,
            description = "Use Android dynamic (Material You) colors instead of the fixed color scheme",
            label = "Dynamic color",
            get = { it.appearance.useDynamicColor },
            set = { s, v -> s.copy(appearance = s.appearance.copy(useDynamicColor = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.darkMode",
            type = ConfigValueType.Enum(
                DarkModePreference.entries.map {
                    EnumOption(
                        it.name, it.displayName,
                        when (it) {
                            DarkModePreference.SYSTEM -> listOf("system", "auto", "automatic", "follow system")
                            DarkModePreference.LIGHT -> listOf("light", "day", "light mode", "light theme")
                            DarkModePreference.DARK -> listOf("dark", "night", "dark mode", "dark theme")
                        }
                    )
                }
            ),
            description = "Dark/light theme preference",
            label = "Theme",
            get = { it.appearance.darkModePreference.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(darkModePreference = DarkModePreference.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.uiLanguage",
            type = ConfigValueType.Enum(
                AppLanguage.entries.map { EnumOption(it.code, it.displayName, listOf(it.nativeName)) }
            ),
            description = "Language of the app interface",
            label = "Interface language",
            get = { it.appearance.uiLanguage },
            set = { s, v -> s.copy(appearance = s.appearance.copy(uiLanguage = v as String)) },
        ),
        ConfigField(
            path = "appearance.uiScale",
            type = ConfigValueType.Enum(UIScaleOption.entries.map { EnumOption(it.name, it.displayName) }),
            description = "Overall UI size scaling",
            label = "UI scale",
            get = { it.appearance.uiScale.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(uiScale = UIScaleOption.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.fontFamily",
            type = ConfigValueType.Enum(FontFamilyOption.entries.map { EnumOption(it.name, it.displayName) }),
            description = "Font family used across the UI",
            label = "Font",
            get = { it.appearance.fontFamily.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(fontFamily = FontFamilyOption.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.autoCopyToClipboard",
            type = ConfigValueType.Bool,
            description = "Automatically copy each transcription to the clipboard",
            label = "Auto-copy to clipboard",
            get = { it.appearance.autoCopyToClipboard },
            set = { s, v -> s.copy(appearance = s.appearance.copy(autoCopyToClipboard = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.enableHistoryPanel",
            type = ConfigValueType.Bool,
            description = "Keep and show transcription history",
            label = "History",
            get = { it.appearance.enableHistoryPanel },
            set = { s, v -> s.copy(appearance = s.appearance.copy(enableHistoryPanel = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.maxHistoryItems",
            type = ConfigValueType.IntRange(0, 1000),
            description = "Maximum number of history items to keep",
            label = "Max history items",
            get = { it.appearance.maxHistoryItems },
            set = { s, v -> s.copy(appearance = s.appearance.copy(maxHistoryItems = v as Int)) },
        ),
        ConfigField(
            path = "appearance.unlimitedHistory",
            type = ConfigValueType.Bool,
            description = "Keep unlimited history (ignores maxHistoryItems)",
            label = "Unlimited history",
            get = { it.appearance.unlimitedHistory },
            set = { s, v -> s.copy(appearance = s.appearance.copy(unlimitedHistory = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.techieMode",
            type = ConfigValueType.Bool,
            description = "Developer mode: show technical details like logs and processing info",
            label = "Techie mode",
            get = { it.appearance.techieModeEnabled },
            set = { s, v -> s.copy(appearance = s.appearance.copy(techieModeEnabled = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.saveOriginalAudioFiles",
            type = ConfigValueType.Bool,
            description = "Save recorded audio files for playback and reprocessing from history",
            label = "Save audio files",
            get = { it.appearance.saveOriginalAudioFiles },
            set = { s, v -> s.copy(appearance = s.appearance.copy(saveOriginalAudioFiles = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.showKeyboardSwitcher",
            type = ConfigValueType.Bool,
            description = "Show the keyboard switcher button on the main screen",
            label = "Keyboard switcher button",
            get = { it.appearance.showKeyboardSwitcher },
            set = { s, v -> s.copy(appearance = s.appearance.copy(showKeyboardSwitcher = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.keyboardMode",
            type = ConfigValueType.Enum(keyboardModeOptions),
            description = "Active keyboard mode (input canvas)",
            label = "Keyboard mode",
            get = { it.appearance.lastKeyboardInputMode.normalize().name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(lastKeyboardInputMode = KeyboardInputMode.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.presetKeyboardMode",
            type = ConfigValueType.Enum(keyboardModeOptions),
            description = "Keyboard mode bound to the configurable third slot in the top strip",
            label = "Preset mode slot",
            get = { it.appearance.presetKeyboardMode.normalize().name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(presetKeyboardMode = KeyboardInputMode.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.enabledAgentKeyboards",
            type = ConfigValueType.EnumSet(agentKeyboardOptions),
            description = "Coding-agent keyboards enabled in the mode switcher (complete set)",
            label = "Enabled agent keyboards",
            get = { it.appearance.enabledAgentKeyboards },
            set = { s, v ->
                @Suppress("UNCHECKED_CAST")
                s.copy(appearance = s.appearance.copy(enabledAgentKeyboards = v as Set<String>))
            },
        ),
        ConfigField(
            path = "appearance.keyboardLayout",
            type = ConfigValueType.Enum(keyboardLayoutOptions),
            description = "Active typing layout; also sets the dictation input language",
            label = "Keyboard layout",
            get = { it.appearance.currentKeyboardLayout.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(currentKeyboardLayout = KeyboardLayout.valueOf(v as String))) },
        ),
        ConfigField(
            path = "appearance.enabledKeyboardLayouts",
            type = ConfigValueType.EnumSet(keyboardLayoutOptions),
            description = "Language layouts available in the layout switcher (complete set)",
            label = "Enabled layouts",
            get = { it.appearance.enabledKeyboardLayouts.map { l -> l.name }.toSet() },
            set = { s, v ->
                @Suppress("UNCHECKED_CAST")
                val layouts = (v as Set<String>).map { name -> KeyboardLayout.valueOf(name) }.toSet()
                s.copy(appearance = s.appearance.copy(enabledKeyboardLayouts = layouts.ifEmpty { setOf(KeyboardLayout.ENGLISH) }))
            },
        ),
        ConfigField(
            path = "appearance.perAppLayoutMemory",
            type = ConfigValueType.Bool,
            description = "Remember and auto-restore the last used layout per app",
            label = "Per-app layout memory",
            get = { it.appearance.perAppLayoutMemoryEnabled },
            set = { s, v -> s.copy(appearance = s.appearance.copy(perAppLayoutMemoryEnabled = v as Boolean)) },
        ),
        ConfigField(
            path = "appearance.recentEmojis",
            type = ConfigValueType.OpaqueJson,
            description = "Recently used emojis (managed automatically)",
            label = "Recent emojis",
            includeInPrompt = false,
            get = { snapshot -> gson.toJsonTree(snapshot.appearance.recentEmojis) },
            set = { s, v ->
                val emojis = (v as JsonElement).asJsonArray.map { e -> e.asString }
                s.copy(appearance = s.appearance.copy(recentEmojis = emojis))
            },
        ),
    )

    // ------------------------------------------------------------------
    // voiceModes.*
    // ------------------------------------------------------------------

    private fun voiceModeFields(snapshot: ConfigSnapshot): List<ConfigField> = listOf(
        ConfigField(
            path = "voiceModes.selected",
            type = ConfigValueType.Enum(
                snapshot.voiceModes.map { EnumOption(it.id, it.name) }
            ),
            description = "Active voice processing mode applied to dictation",
            label = "Voice mode",
            get = { it.selectedModeId },
            set = { s, v -> s.copy(selectedModeId = v as String) },
        ),
        ConfigField(
            path = "voiceModes.modes",
            type = ConfigValueType.OpaqueJson,
            description = "Full voice mode definitions (edit in the Voice Modes screen)",
            label = "Voice modes",
            includeInPrompt = false,
            get = { snap -> gson.toJsonTree(snap.voiceModes) },
            set = { s, v ->
                val type = object : com.google.gson.reflect.TypeToken<List<com.hyperwhisper.data.VoiceMode>>() {}.type
                val modes: List<com.hyperwhisper.data.VoiceMode> = gson.fromJson(v as JsonElement, type)
                if (modes.isEmpty()) s else s.copy(voiceModes = modes)
            },
        ),
    )

    /** Stable JSON for OpaqueJson comparison in import diffs. */
    fun jsonEquals(a: JsonElement?, b: JsonElement?): Boolean = a == b || JsonParser.parseString(
        gson.toJson(a ?: com.google.gson.JsonNull.INSTANCE)
    ) == JsonParser.parseString(gson.toJson(b ?: com.google.gson.JsonNull.INSTANCE))
}
