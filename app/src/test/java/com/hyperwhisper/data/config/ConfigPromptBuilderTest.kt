package com.hyperwhisper.data.config

import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.data.KeyboardLayout
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.UIScaleOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The configuration prompt is the LLM's only window into the option space —
 * it must enumerate every prompt-visible path and every allowed enum value,
 * embed the current values, and pin the output contract.
 */
class ConfigPromptBuilderTest {

    private val prompt = ConfigPromptBuilder.buildFor(ConfigTestFixtures.defaultSnapshot())

    @Test
    fun containsOutputContract() {
        assertTrue(prompt.contains("\"changes\""))
        assertTrue(prompt.contains("Output ONLY the JSON object"))
    }

    @Test
    fun containsAllPromptVisiblePaths() {
        val fields = ConfigSchema.fields(ConfigTestFixtures.defaultSnapshot())
        for (field in fields.filter { it.includeInPrompt }) {
            // Paths are rendered as nested keys; the leaf key must appear.
            val leaf = field.path.substringAfterLast('.')
            assertTrue("prompt missing leaf key for ${field.path}", prompt.contains("\"$leaf\""))
        }
    }

    @Test
    fun containsEveryColorScheme() {
        for (scheme in ColorSchemeOption.entries) {
            assertTrue("prompt missing color scheme ${scheme.name}", prompt.contains(scheme.name))
        }
    }

    @Test
    fun containsEveryProviderAndScaleOption() {
        for (provider in ApiProvider.entries) {
            assertTrue("prompt missing ASR provider ${provider.name}", prompt.contains(provider.name))
        }
        for (provider in LlmProvider.entries) {
            assertTrue("prompt missing LLM provider ${provider.name}", prompt.contains(provider.name))
        }
        for (scale in UIScaleOption.entries) {
            assertTrue("prompt missing UI scale ${scale.name}", prompt.contains(scale.name))
        }
        for (layout in KeyboardLayout.entries) {
            assertTrue("prompt missing layout ${layout.name}", prompt.contains(layout.name))
        }
    }

    @Test
    fun embedsCurrentValues() {
        // Defaults from the fixture snapshot. ApiSettings.modelId is derived
        // from provider.defaultModels.first(); for the default OPENAI provider
        // that is "gpt-4o-transcribe" (no longer the legacy "whisper-1").
        assertTrue(prompt.contains("OCEAN_DEEP"))
        assertTrue(prompt.contains("gpt-4o-transcribe"))
    }

    @Test
    fun hidesPromptExcludedFields() {
        assertFalse("per-provider URLs bloat the prompt", prompt.contains("transcription.providers"))
        assertFalse(prompt.contains("recentEmojis"))
        assertFalse(prompt.contains("whisperModelPath"))
    }
}
