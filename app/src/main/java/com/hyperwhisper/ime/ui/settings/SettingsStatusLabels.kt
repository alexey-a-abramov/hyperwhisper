package com.hyperwhisper.ui.settings

import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LlmProvider

/**
 * Pure helpers for deriving status-line labels from [ApiSettings].
 * Kept in a non-Composable file so they can be unit-tested without Compose tooling.
 */
object SettingsStatusLabels {

    /** Label for the active transcription source (cloud provider · model, or on-device). */
    fun transcriptionLabel(settings: ApiSettings): String {
        if (settings.localModelSettings.useLocalWhisper) return "On-device · Whisper"
        val model = settings.modelId.ifBlank { "—" }
        return "${settings.provider.displayName} · $model"
    }

    /** Label for the post-processing LLM (or "Off" when disabled). */
    fun postProcessingLabel(settings: ApiSettings): String {
        val cfg = settings.llmConfig
        if (cfg.provider == LlmProvider.NONE) return "Off"
        if (settings.localModelSettings.useLocalGemma) return "On-device · Gemma"
        val model = cfg.modelId.ifBlank { "—" }
        return "${cfg.provider.displayName} · $model"
    }

    /** Whether post-processing is currently active. */
    fun postProcessingActive(settings: ApiSettings): Boolean =
        settings.llmConfig.provider != LlmProvider.NONE

    /** Trailing pill text for a category card, or null when no pill should render. */
    fun categoryTrailing(category: SettingsCategory, settings: ApiSettings): String? {
        return when (category) {
            SettingsCategory.TRANSCRIPTION -> {
                if (settings.localModelSettings.useLocalWhisper) "On-device"
                else settings.provider.displayName
            }
            SettingsCategory.POST_PROCESSING -> {
                when {
                    settings.llmConfig.provider == LlmProvider.NONE -> "Off"
                    settings.localModelSettings.useLocalGemma -> "On-device"
                    else -> settings.llmConfig.provider.displayName
                }
            }
            else -> null
        }
    }
}
