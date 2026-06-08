package com.hyperwhisper.data.config

import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.LlmProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * API keys must never reach the export document or the LLM prompt — neither
 * via the registry (which exposes no key paths) nor via scrubbing-bypass.
 */
class ConfigRedactionTest {

    private val ASR_SENTINEL = "sk-SENTINEL-ASR-1234"
    private val LLM_SENTINEL = "sk-SENTINEL-LLM-5678"
    private val LEGACY_SENTINEL = "sk-SENTINEL-LEGACY-9999"

    private fun snapshotWithSecrets(): ConfigSnapshot {
        val default = ConfigTestFixtures.defaultSnapshot()
        return default.copy(
            api = default.api.copy(
                apiKeys = mapOf(ApiProvider.OPENAI to ASR_SENTINEL),
                llmConfig = default.api.llmConfig.copy(
                    apiKey = LEGACY_SENTINEL,
                    apiKeys = mapOf(LlmProvider.OPENAI to LLM_SENTINEL),
                ),
            ),
        )
    }

    private fun assertNoSentinel(text: String, context: String) {
        assertFalse("$context leaked ASR key", text.contains(ASR_SENTINEL))
        assertFalse("$context leaked LLM key", text.contains(LLM_SENTINEL))
        assertFalse("$context leaked legacy LLM key", text.contains(LEGACY_SENTINEL))
    }

    @Test
    fun scrubbedRemovesAllKeys() {
        val scrubbed = snapshotWithSecrets().scrubbed()
        assertTrue(scrubbed.api.apiKeys.isEmpty())
        assertTrue(scrubbed.api.llmConfig.apiKeys.isEmpty())
        assertTrue(scrubbed.api.llmConfig.apiKey.isEmpty())
    }

    @Test
    fun exportContainsNoKeys_evenUnscrubbed() {
        // The registry exposes no key paths, so even an unscrubbed snapshot
        // must serialize clean — scrubbing is defense in depth on top.
        val snapshot = snapshotWithSecrets()
        val jsonc = JsoncWriter.write(snapshot, ConfigSchema.fields(snapshot))
        assertNoSentinel(jsonc, "export (unscrubbed)")
    }

    @Test
    fun exportContainsNoKeys_scrubbed() {
        val scrubbed = snapshotWithSecrets().scrubbed()
        val jsonc = JsoncWriter.write(scrubbed, ConfigSchema.fields(scrubbed))
        assertNoSentinel(jsonc, "export (scrubbed)")
    }

    @Test
    fun promptContainsNoKeys() {
        val prompt = ConfigPromptBuilder.buildFor(snapshotWithSecrets().scrubbed())
        assertNoSentinel(prompt, "prompt")
    }

    @Test
    fun registryExposesNoKeyPaths() {
        val fields = ConfigSchema.fields(ConfigTestFixtures.defaultSnapshot())
        val keyLike = fields.filter { it.path.lowercase().contains("apikey") || it.path.lowercase().contains("secret") }
        assertTrue("registry must not expose key paths: $keyLike", keyLike.isEmpty())
    }
}
