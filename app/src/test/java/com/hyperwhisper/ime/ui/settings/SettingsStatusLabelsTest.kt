package com.hyperwhisper.ui.settings

import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.LocalModelSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStatusLabelsTest {

    private fun cloudSettings(
        provider: ApiProvider = ApiProvider.OPENAI,
        modelId: String = "whisper-1",
        llm: LlmConfig = LlmConfig(provider = LlmProvider.NONE, modelId = "none"),
        localWhisper: Boolean = false,
        localGemma: Boolean = false
    ) = ApiSettings(
        provider = provider,
        modelId = modelId,
        llmConfig = llm,
        localModelSettings = LocalModelSettings(
            useLocalWhisper = localWhisper,
            useLocalGemma = localGemma
        )
    )

    @Test
    fun transcriptionLabel_cloudShowsProviderAndModel() {
        val s = cloudSettings(provider = ApiProvider.OPENAI, modelId = "whisper-1")
        assertEquals("${ApiProvider.OPENAI.displayName} · whisper-1", SettingsStatusLabels.transcriptionLabel(s))
    }

    @Test
    fun transcriptionLabel_blankModelRendersDash() {
        val s = cloudSettings(provider = ApiProvider.OPENAI, modelId = "")
        assertEquals("${ApiProvider.OPENAI.displayName} · —", SettingsStatusLabels.transcriptionLabel(s))
    }

    @Test
    fun transcriptionLabel_localOverridesCloud() {
        val s = cloudSettings(localWhisper = true)
        assertEquals("On-device · Whisper", SettingsStatusLabels.transcriptionLabel(s))
    }

    @Test
    fun postProcessing_offWhenLlmIsNone() {
        val s = cloudSettings(llm = LlmConfig(provider = LlmProvider.NONE, modelId = "none"))
        assertEquals("Off", SettingsStatusLabels.postProcessingLabel(s))
        assertFalse(SettingsStatusLabels.postProcessingActive(s))
    }

    @Test
    fun postProcessing_showsProviderAndModelWhenActive() {
        val s = cloudSettings(llm = LlmConfig(provider = LlmProvider.OPENAI, modelId = "gpt-4o-mini"))
        assertEquals("OpenAI · gpt-4o-mini", SettingsStatusLabels.postProcessingLabel(s))
        assertTrue(SettingsStatusLabels.postProcessingActive(s))
    }

    @Test
    fun postProcessing_localGemmaOverridesProvider() {
        val s = cloudSettings(
            llm = LlmConfig(provider = LlmProvider.OPENAI, modelId = "gpt-4o"),
            localGemma = true
        )
        assertEquals("On-device · Gemma", SettingsStatusLabels.postProcessingLabel(s))
    }

    @Test
    fun categoryTrailing_transcriptionPill() {
        val cloud = cloudSettings(provider = ApiProvider.GROQ)
        assertEquals(ApiProvider.GROQ.displayName, SettingsStatusLabels.categoryTrailing(SettingsCategory.TRANSCRIPTION, cloud))

        val local = cloudSettings(localWhisper = true)
        assertEquals("On-device", SettingsStatusLabels.categoryTrailing(SettingsCategory.TRANSCRIPTION, local))
    }

    @Test
    fun categoryTrailing_postProcessingPillReflectsLlmState() {
        val off = cloudSettings()
        assertEquals("Off", SettingsStatusLabels.categoryTrailing(SettingsCategory.POST_PROCESSING, off))

        val active = cloudSettings(llm = LlmConfig(provider = LlmProvider.GEMINI, modelId = "gemini-1.5-flash"))
        assertEquals("Google Gemini", SettingsStatusLabels.categoryTrailing(SettingsCategory.POST_PROCESSING, active))

        val localGemma = cloudSettings(localGemma = true, llm = LlmConfig(provider = LlmProvider.OPENAI, modelId = "x"))
        assertEquals("On-device", SettingsStatusLabels.categoryTrailing(SettingsCategory.POST_PROCESSING, localGemma))
    }

    @Test
    fun categoryTrailing_otherCategoriesReturnNull() {
        val s = cloudSettings()
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.APPEARANCE, s))
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.VOICE_MODES, s))
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.KEYBOARD_BEHAVIOR, s))
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.UPDATES, s))
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.ABOUT, s))
        assertNull(SettingsStatusLabels.categoryTrailing(SettingsCategory.ADVANCED, s))
    }

    @Test
    fun settingsRoute_homeIsSingleton() {
        val a: SettingsRoute = SettingsRoute.Home
        val b: SettingsRoute = SettingsRoute.Home
        assertTrue(a === b)
    }

    @Test
    fun settingsRoute_detailEqualsByCategory() {
        val a = SettingsRoute.Detail(SettingsCategory.TRANSCRIPTION)
        val b = SettingsRoute.Detail(SettingsCategory.TRANSCRIPTION)
        val c = SettingsRoute.Detail(SettingsCategory.APPEARANCE)
        assertEquals(a, b)
        assertFalse(a == c)
    }
}
