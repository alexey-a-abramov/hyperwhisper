package com.hyperwhisper.network

import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.VoiceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure two-step (transcribe → LLM post-process) routing
 * decision, covering the cross-product of provider, voice mode, LLM config,
 * and translation that the keyboard relies on.
 */
class ProcessingRouterDecisionTest {

    private val verbatim = VoiceMode(id = "verbatim", name = "Verbatim")
    private val transform = VoiceMode(id = "polite", name = "Polite")

    private fun settings(
        provider: ApiProvider = ApiProvider.OPENAI,
        llm: LlmProvider = LlmProvider.OPENAI,
        input: String = "",
        output: String = "",
    ) = ApiSettings(
        provider = provider,
        llmConfig = LlmConfig(provider = llm),
        inputLanguage = input,
        outputLanguage = output,
    )

    @Test
    fun llmDisabled_neverTwoStep() {
        assertFalse(ProcessingRouter.needsTwoStep(transform, settings(llm = LlmProvider.NONE)))
        assertFalse(ProcessingRouter.needsTwoStep(verbatim, settings(llm = LlmProvider.NONE)))
    }

    @Test
    fun singleStepAudioProviders_neverTwoStep_evenForTransform() {
        listOf(ApiProvider.OPENROUTER, ApiProvider.GEMINI, ApiProvider.ANTIGRAVITY).forEach { p ->
            assertFalse("$p should be single-step", ProcessingRouter.needsTwoStep(transform, settings(provider = p)))
        }
    }

    @Test
    fun huggingFace_alwaysTwoStep() {
        assertTrue(ProcessingRouter.needsTwoStep(transform, settings(provider = ApiProvider.HUGGINGFACE)))
        assertTrue(ProcessingRouter.needsTwoStep(verbatim, settings(provider = ApiProvider.HUGGINGFACE)))
    }

    @Test
    fun verbatim_onlyTwoStepWhenTranslating() {
        // no output language → no translation → single step
        assertFalse(ProcessingRouter.needsTwoStep(verbatim, settings(input = "en", output = "")))
        // output equals input → no translation → single step
        assertFalse(ProcessingRouter.needsTwoStep(verbatim, settings(input = "en", output = "en")))
        // output differs → translation needed → two step
        assertTrue(ProcessingRouter.needsTwoStep(verbatim, settings(input = "en", output = "ru")))
    }

    @Test
    fun transformationMode_withCloudLlm_isTwoStep() {
        assertTrue(ProcessingRouter.needsTwoStep(transform, settings(provider = ApiProvider.OPENAI)))
        assertTrue(ProcessingRouter.needsTwoStep(transform, settings(provider = ApiProvider.GROQ)))
    }
}
