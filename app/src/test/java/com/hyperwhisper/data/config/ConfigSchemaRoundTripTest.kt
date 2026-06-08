package com.hyperwhisper.data.config

import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.data.DarkModePreference
import com.hyperwhisper.data.FontFamilyOption
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.KeyboardLayout
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.LocalModelSettings
import com.hyperwhisper.data.ProviderConfig
import com.hyperwhisper.data.UIScaleOption
import com.hyperwhisper.data.VoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Export → import → apply must reproduce the original snapshot exactly:
 * a heavily-customized config is rendered to JSONC, diffed against a default
 * config, and the resulting patch folded onto the default must equal the
 * customized one. This pins the writer, the parser, and every registry
 * getter/setter pair to each other.
 */
class ConfigSchemaRoundTripTest {

    private fun customizedSnapshot(): ConfigSnapshot {
        val default = ConfigTestFixtures.defaultSnapshot()
        return default.copy(
            api = default.api.copy(
                provider = ApiProvider.GROQ,
                modelId = "whisper-large-v3",
                inputLanguage = "es",
                outputLanguage = "de",
                providerConfigs = mapOf(
                    ApiProvider.DEEPGRAM to ProviderConfig(
                        customBaseUrl = "https://dg.example/",
                        requiresAuth = true,
                    )
                ),
                llmConfig = LlmConfig(
                    provider = LlmProvider.DEEPSEEK,
                    modelId = "deepseek-chat",
                ),
                localModelSettings = LocalModelSettings(
                    whisperModelPath = "/sdcard/models/whisper-base.bin",
                    useLocalWhisper = true,
                    threads = 8,
                ),
            ),
            appearance = default.appearance.copy(
                colorScheme = ColorSchemeOption.RUBY_RED,
                darkModePreference = DarkModePreference.DARK,
                uiLanguage = "ru",
                uiScale = UIScaleOption.LARGE,
                fontFamily = FontFamilyOption.MONOSPACE,
                techieModeEnabled = true,
                maxHistoryItems = 50,
                lastKeyboardInputMode = KeyboardInputMode.CODE,
                currentKeyboardLayout = KeyboardLayout.RUSSIAN,
                enabledKeyboardLayouts = setOf(KeyboardLayout.ENGLISH, KeyboardLayout.RUSSIAN),
                enabledAgentKeyboards = setOf("AGENT_CLAUDE_CODE"),
                recentEmojis = listOf("😀", "🎉"),
            ),
            voiceModes = ConfigTestFixtures.defaultVoiceModes() +
                VoiceMode(id = "custom", name = "Custom", systemPrompt = "Custom prompt"),
            selectedModeId = "configuration",
        )
    }

    @Test
    fun exportImportFoldReproducesSnapshot() {
        val customized = customizedSnapshot()
        val default = ConfigTestFixtures.defaultSnapshot()

        val jsonc = JsoncWriter.write(customized, ConfigSchema.fields(customized))
        val patch = ConfigPatchParser.parseImport(jsonc, default)

        assertTrue("import must parse", patch != null)
        assertTrue(
            "no validation errors expected, got: ${patch!!.errors}",
            patch.errors.isEmpty()
        )
        assertTrue("diff must not be empty", patch.valid.isNotEmpty())

        val folded = ConfigPatchApplier.fold(default, patch.valid)
        assertEquals(customized, folded)
    }

    @Test
    fun importOfOwnExportYieldsNoDifferences() {
        val customized = customizedSnapshot()
        val jsonc = JsoncWriter.write(customized, ConfigSchema.fields(customized))
        val patch = ConfigPatchParser.parseImport(jsonc, customized)

        assertTrue(patch != null)
        assertTrue("self-import must be a no-op, got: ${patch!!.valid.map { it.field.path }}", patch.isEmpty)
    }

    @Test
    fun exportedDocumentParsesDespiteComments() {
        val customized = customizedSnapshot()
        val jsonc = JsoncWriter.write(customized, ConfigSchema.fields(customized))
        assertTrue("export should contain comments", jsonc.contains("//"))
        assertTrue(JsoncParser.parseObject(jsonc) != null)
        assertTrue(jsonc.contains(JsoncWriter.FORMAT_ID))
    }
}
