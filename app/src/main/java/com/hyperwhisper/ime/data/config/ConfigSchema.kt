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
 *
 * ------------------------------------------------------------------------
 * PATH NAMESPACES (pipeline-aligned)
 * ------------------------------------------------------------------------
 * Paths are grouped by the audio→text pipeline stage they belong to, NOT by
 * the data class that stores them. The dot-path is the public identity (it
 * appears in exports, prompts, and patches); the get/set lambdas still target
 * the real data classes, which are NOT renamed. The six top-level namespaces:
 *
 *  - input.*          audio capture
 *  - transcription.*  ASR provider/model/key/baseUrl/input language, local Whisper
 *  - postProcessing.* LLM engine/config, output language/translation, voice modes
 *  - output.*         insertion + keyboard behavior (auto-copy, layouts/localities, per-app)
 *  - appearance.*     theme, dynamic color, dark mode, UI scale, font, UI language
 *  - system.*         device/runtime: history retention, techie mode, on-device inference knobs
 *
 * ------------------------------------------------------------------------
 * OLD → NEW PATH MAP (renamed while the registry is unreleased)
 * ------------------------------------------------------------------------
 *  transcription.provider                     -> transcription.provider          (unchanged)
 *  transcription.modelId                      -> transcription.modelId           (unchanged)
 *  transcription.inputLanguage                -> transcription.inputLanguage     (unchanged)
 *  transcription.outputLanguage               -> postProcessing.outputLanguage
 *  transcription.providers.<P>.baseUrl        -> transcription.providers.<P>.baseUrl      (unchanged)
 *  transcription.providers.<P>.requiresAuth   -> transcription.providers.<P>.requiresAuth (unchanged)
 *  llm.provider                               -> postProcessing.provider
 *  llm.modelId                                -> postProcessing.modelId
 *  llm.providers.<P>.baseUrl                  -> postProcessing.providers.<P>.baseUrl
 *  llm.providers.<P>.requiresAuth             -> postProcessing.providers.<P>.requiresAuth
 *  localModels.useLocalWhisper                -> transcription.useLocalWhisper
 *  localModels.whisperModelPath               -> transcription.whisperModelPath
 *  localModels.useLocalGemma                  -> postProcessing.useLocalLlm
 *  localModels.llmModelPath                   -> postProcessing.localModelPath
 *  localModels.threads                        -> system.localInferenceThreads
 *  localModels.autoDiscover                   -> system.autoDiscoverLocalModels
 *  appearance.colorScheme                     -> appearance.colorScheme          (unchanged)
 *  appearance.useDynamicColor                 -> appearance.useDynamicColor       (unchanged)
 *  appearance.darkMode                        -> appearance.darkMode             (unchanged)
 *  appearance.uiLanguage                      -> appearance.uiLanguage           (unchanged)
 *  appearance.uiScale                         -> appearance.uiScale              (unchanged)
 *  appearance.fontFamily                      -> appearance.fontFamily           (unchanged)
 *  appearance.autoCopyToClipboard             -> output.autoCopyToClipboard
 *  appearance.enableHistoryPanel              -> system.enableHistory
 *  appearance.maxHistoryItems                 -> system.maxHistoryItems
 *  appearance.unlimitedHistory                -> system.unlimitedHistory
 *  appearance.techieMode                      -> system.techieMode
 *  appearance.saveOriginalAudioFiles          -> input.keepOriginalAudio
 *  appearance.showKeyboardSwitcher            -> output.showKeyboardSwitcher
 *  appearance.keyboardMode                    -> output.keyboardMode
 *  appearance.presetKeyboardMode              -> output.presetKeyboardMode
 *  appearance.enabledAgentKeyboards           -> output.enabledAgentKeyboards
 *  appearance.keyboardLayout                  -> output.keyboardLayout
 *  appearance.enabledKeyboardLayouts          -> output.enabledKeyboardLayouts
 *  appearance.perAppLayoutMemory              -> output.perAppLayoutMemory
 *  appearance.recentEmojis                    -> (REMOVED — see snapshot-membership note below)
 *  voiceModes.selected                        -> postProcessing.voiceModes.selected
 *  voiceModes.modes                           -> postProcessing.voiceModes.modes
 *
 * Notes on the cross-cutting moves:
 *  - The on-device inference knobs `threads` and `autoDiscover` apply to BOTH
 *    local Whisper (ASR) and the local LLM (post-processing), so they are
 *    device/runtime concerns rather than belonging to a single pipeline stage:
 *    they live under system.*. The engine TOGGLES and model PATHS, being
 *    stage-specific, follow their stage (transcription.* / postProcessing.*).
 *  - History retention (enable/max/unlimited) is on-device data management, so
 *    it moved out of appearance.* into system.*; only true look-and-feel
 *    settings (theme/color/dark mode/scale/font/UI language) remain there.
 *
 * ------------------------------------------------------------------------
 * SNAPSHOT MEMBERSHIP DECISION (export/import scope)
 * ------------------------------------------------------------------------
 * [ConfigSnapshot] covers exactly api + appearance + voiceModes (+ the
 * selected voice-mode id). The following are EXPLICITLY EXCLUDED from
 * export/import as device-local / ephemeral state — they are deliberately NOT
 * exposed as [ConfigField]s, so future additions get classified on purpose:
 *  - the per-app layout MEMORY MAP (PerAppLayoutMemory device store; the
 *    output.perAppLayoutMemory ENABLE toggle is a normal exported preference,
 *    but the remembered app→layout associations are not portable);
 *  - recentlyUsedProviderModels MRU tracking (ProviderModelTrackingRepository);
 *  - recentEmojis (managed automatically by the emoji keyboard — kept in
 *    AppearanceSettings for runtime use, but no longer round-tripped);
 *  - the built-in "Configuration" voice mode itself, which is device-local
 *    machinery rather than user content (the voiceModes list is exported, but
 *    this entry is treated as ephemeral/built-in).
 */
object ConfigSchema {

    private val gson = Gson()

    /**
     * The registry is snapshot-aware because voice-mode options (ids/names)
     * are user-editable and must reflect the live list.
     */
    fun fields(snapshot: ConfigSnapshot): List<ConfigField> =
        inputFields() +
            transcriptionFields() +
            postProcessingFields(snapshot) +
            outputFields() +
            appearanceFields() +
            systemFields()

    /** Fields addressable by patch path (everything). */
    fun byPath(snapshot: ConfigSnapshot): Map<String, ConfigField> =
        fields(snapshot).associateBy { it.path.lowercase() }

    // ------------------------------------------------------------------
    // input.*  (audio capture)
    // ------------------------------------------------------------------

    private fun inputFields(): List<ConfigField> = listOf(
        ConfigField(
            path = "input.keepOriginalAudio",
            type = ConfigValueType.Bool,
            description = "Save recorded audio files for playback and reprocessing from history",
            label = "Save audio files",
            get = { it.appearance.saveOriginalAudioFiles },
            set = { s, v -> s.copy(appearance = s.appearance.copy(saveOriginalAudioFiles = v as Boolean)) },
        ),
    )

    // ------------------------------------------------------------------
    // transcription.*  (ASR provider/model/input language + local Whisper)
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
                path = "transcription.useLocalWhisper",
                type = ConfigValueType.Bool,
                description = "Transcribe on-device with a local Whisper model instead of a cloud provider",
                label = "Local Whisper",
                get = { it.api.localModelSettings.useLocalWhisper },
                set = { s, v ->
                    s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(useLocalWhisper = v as Boolean)))
                },
            ),
            ConfigField(
                path = "transcription.whisperModelPath",
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
                path = "transcription.statisticsPrediction",
                type = ConfigValueType.Bool,
                description = "Estimate local transcription progress from gathered timing statistics instead of a fixed heuristic",
                label = "Statistics-based progress",
                get = { it.api.localModelSettings.statisticsPrediction ?: true },
                set = { s, v ->
                    s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(statisticsPrediction = v as Boolean)))
                },
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
    // postProcessing.*  (LLM engine/config, output language, voice modes)
    // ------------------------------------------------------------------

    private fun postProcessingFields(snapshot: ConfigSnapshot): List<ConfigField> {
        val providerOptions = LlmProvider.entries.map { EnumOption(it.name, it.displayName) }
        val modelDocs = LlmProvider.entries
            .filter { it != LlmProvider.NONE }
            .joinToString("; ") { "${it.name}: ${it.defaultModels.joinToString("|")}" }

        val base = listOf(
            ConfigField(
                path = "postProcessing.provider",
                type = ConfigValueType.Enum(providerOptions),
                description = "LLM provider for post-processing transcribed text (grammar fixes, tone transforms, this configuration mode). NONE disables post-processing",
                label = "LLM provider",
                get = { it.api.llmConfig.provider.name },
                set = { s, v ->
                    s.copy(api = s.api.copy(llmConfig = s.api.llmConfig.copy(provider = LlmProvider.valueOf(v as String))))
                },
            ),
            ConfigField(
                path = "postProcessing.modelId",
                type = ConfigValueType.Text,
                description = "Model ID for the LLM provider. Typical models per provider: $modelDocs",
                label = "LLM model",
                get = { it.api.llmConfig.modelId },
                set = { s, v -> s.copy(api = s.api.copy(llmConfig = s.api.llmConfig.copy(modelId = v as String))) },
            ),
            ConfigField(
                path = "postProcessing.outputLanguage",
                type = ConfigValueType.LanguageCode(emptyMeaning = "No translation"),
                description = "Translation target language as ISO-639-1 code; \"\" = keep original language (no translation)",
                label = "Output language",
                get = { it.api.outputLanguage },
                set = { s, v -> s.copy(api = s.api.copy(outputLanguage = v as String)) },
            ),
            ConfigField(
                path = "postProcessing.useLocalLlm",
                type = ConfigValueType.Bool,
                description = "Post-process on-device with a local LLM (Gemma .task or llama.cpp .gguf) instead of a cloud LLM",
                label = "Local LLM",
                get = { it.api.localModelSettings.useLocalGemma },
                set = { s, v ->
                    s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(useLocalGemma = v as Boolean)))
                },
            ),
            ConfigField(
                path = "postProcessing.localModelPath",
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

        val perProvider = LlmProvider.entries.filter { it != LlmProvider.NONE }.flatMap { provider ->
            listOf(
                ConfigField(
                    path = "postProcessing.providers.${provider.name}.baseUrl",
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
                    path = "postProcessing.providers.${provider.name}.requiresAuth",
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

        val voiceModes = listOf(
            ConfigField(
                path = "postProcessing.voiceModes.selected",
                type = ConfigValueType.Enum(
                    snapshot.voiceModes.map { EnumOption(it.id, it.name) }
                ),
                description = "Active voice processing mode applied to dictation",
                label = "Voice mode",
                get = { it.selectedModeId },
                set = { s, v -> s.copy(selectedModeId = v as String) },
            ),
            ConfigField(
                path = "postProcessing.voiceModes.modes",
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

        return base + perProvider + voiceModes
    }

    // ------------------------------------------------------------------
    // output.*  (insertion + keyboard behavior)
    // ------------------------------------------------------------------

    /** Keyboard mode options with the spoken synonyms the old voice-command
     *  flow accepted (VoiceCommand.getKeyboardMode). The dead modes
     *  (NUMPAD/SYSTEM_KEYS/VIBE_CODING) are NOT offered as canonical choices,
     *  but their names survive as CODE synonyms so a stored/imported value
     *  still resolves (and is coerced to CODE) instead of erroring. */
    private val keyboardModeOptions: List<EnumOption> = listOf(
        EnumOption("DICTATION", "Voice", listOf("voice", "dictation", "mic", "voice input")),
        EnumOption("QWERTY", "Text", listOf("text", "abc", "qwerty", "letters", "alphabet", "typing")),
        EnumOption(
            "CODE", "Code",
            listOf(
                "code", "coding", "programmer", "symbols",
                // Dead modes tolerated on parse → coerced to CODE.
                "numpad", "system keys", "system_keys", "vibe coding", "vibe_coding",
            ),
        ),
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

    private fun outputFields(): List<ConfigField> = listOf(
        ConfigField(
            path = "output.autoCopyToClipboard",
            type = ConfigValueType.Bool,
            description = "Automatically copy each transcription to the clipboard",
            label = "Auto-copy to clipboard",
            get = { it.appearance.autoCopyToClipboard },
            set = { s, v -> s.copy(appearance = s.appearance.copy(autoCopyToClipboard = v as Boolean)) },
        ),
        ConfigField(
            path = "output.showKeyboardSwitcher",
            type = ConfigValueType.Bool,
            description = "Show the keyboard switcher button on the main screen",
            label = "Keyboard switcher button",
            get = { it.appearance.showKeyboardSwitcher },
            set = { s, v -> s.copy(appearance = s.appearance.copy(showKeyboardSwitcher = v as Boolean)) },
        ),
        ConfigField(
            path = "output.keyboardMode",
            type = ConfigValueType.Enum(keyboardModeOptions),
            description = "Active keyboard mode (input canvas)",
            label = "Keyboard mode",
            get = { it.appearance.lastKeyboardInputMode.normalize().name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(lastKeyboardInputMode = KeyboardInputMode.valueOf(v as String))) },
        ),
        ConfigField(
            path = "output.presetKeyboardMode",
            type = ConfigValueType.Enum(keyboardModeOptions),
            description = "Keyboard mode bound to the configurable third slot in the top strip",
            label = "Preset mode slot",
            get = { it.appearance.presetKeyboardMode.normalize().name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(presetKeyboardMode = KeyboardInputMode.valueOf(v as String))) },
        ),
        ConfigField(
            path = "output.enabledAgentKeyboards",
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
            path = "output.keyboardLayout",
            type = ConfigValueType.Enum(keyboardLayoutOptions),
            description = "Active typing layout; also sets the dictation input language",
            label = "Keyboard layout",
            get = { it.appearance.currentKeyboardLayout.name },
            set = { s, v -> s.copy(appearance = s.appearance.copy(currentKeyboardLayout = KeyboardLayout.valueOf(v as String))) },
        ),
        ConfigField(
            path = "output.enabledKeyboardLayouts",
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
            path = "output.perAppLayoutMemory",
            type = ConfigValueType.Bool,
            description = "Remember and auto-restore the last used layout per app",
            label = "Per-app layout memory",
            get = { it.appearance.perAppLayoutMemoryEnabled },
            set = { s, v -> s.copy(appearance = s.appearance.copy(perAppLayoutMemoryEnabled = v as Boolean)) },
        ),
    )

    // ------------------------------------------------------------------
    // appearance.*  (look & feel only)
    // ------------------------------------------------------------------

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
    )

    // ------------------------------------------------------------------
    // system.*  (device/runtime: history retention, techie mode, on-device inference)
    // ------------------------------------------------------------------

    private fun systemFields(): List<ConfigField> = listOf(
        ConfigField(
            path = "system.enableHistory",
            type = ConfigValueType.Bool,
            description = "Keep and show transcription history",
            label = "History",
            get = { it.appearance.enableHistoryPanel },
            set = { s, v -> s.copy(appearance = s.appearance.copy(enableHistoryPanel = v as Boolean)) },
        ),
        ConfigField(
            path = "system.maxHistoryItems",
            type = ConfigValueType.IntRange(0, 1000),
            description = "Maximum number of history items to keep",
            label = "Max history items",
            get = { it.appearance.maxHistoryItems },
            set = { s, v -> s.copy(appearance = s.appearance.copy(maxHistoryItems = v as Int)) },
        ),
        ConfigField(
            path = "system.unlimitedHistory",
            type = ConfigValueType.Bool,
            description = "Keep unlimited history (ignores maxHistoryItems)",
            label = "Unlimited history",
            get = { it.appearance.unlimitedHistory },
            set = { s, v -> s.copy(appearance = s.appearance.copy(unlimitedHistory = v as Boolean)) },
        ),
        ConfigField(
            path = "system.techieMode",
            type = ConfigValueType.Bool,
            description = "Developer mode: show technical details like logs and processing info",
            label = "Techie mode",
            get = { it.appearance.techieModeEnabled },
            set = { s, v -> s.copy(appearance = s.appearance.copy(techieModeEnabled = v as Boolean)) },
        ),
        ConfigField(
            path = "system.localInferenceThreads",
            type = ConfigValueType.IntRange(1, 16),
            description = "CPU threads for on-device inference (shared by local Whisper and local LLM)",
            label = "Inference threads",
            get = { it.api.localModelSettings.threads },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(threads = v as Int)))
            },
        ),
        ConfigField(
            path = "system.autoDiscoverLocalModels",
            type = ConfigValueType.Bool,
            description = "Automatically discover model files on device storage",
            label = "Auto-discover models",
            get = { it.api.localModelSettings.autoDiscover },
            set = { s, v ->
                s.copy(api = s.api.copy(localModelSettings = s.api.localModelSettings.copy(autoDiscover = v as Boolean)))
            },
        ),
    )

    /** Stable JSON for OpaqueJson comparison in import diffs. */
    fun jsonEquals(a: JsonElement?, b: JsonElement?): Boolean = a == b || JsonParser.parseString(
        gson.toJson(a ?: com.google.gson.JsonNull.INSTANCE)
    ) == JsonParser.parseString(gson.toJson(b ?: com.google.gson.JsonNull.INSTANCE))
}
