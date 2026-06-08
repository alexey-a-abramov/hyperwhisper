package com.hyperwhisper.data.config

import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable snapshot of the full user-facing configuration. This is the value
 * the [ConfigSchema] field registry reads from and writes to (via pure
 * copy-based setters); it is what gets rendered to JSONC for the LLM prompt,
 * the export feature, and the import diff.
 *
 * ------------------------------------------------------------------------
 * MEMBERSHIP DECISION — what is (not) exported/imported
 * ------------------------------------------------------------------------
 * The snapshot covers exactly three repositories' worth of state:
 *   - [api]          (ApiSettings: ASR + LLM provider config, local models)
 *   - [appearance]   (AppearanceSettings: theme + keyboard + history + audio)
 *   - [voiceModes]   (+ the [selectedModeId] of the active mode)
 *
 * The following are DELIBERATELY EXCLUDED from export/import as device-local /
 * ephemeral state, and are intentionally NOT surfaced as [ConfigField]s in
 * [ConfigSchema] (so any future field gets classified on purpose, not by
 * accident):
 *   1. The per-app layout MEMORY MAP (PerAppLayoutMemory device store). The
 *      enable/disable preference (output.perAppLayoutMemory) IS exported, but
 *      the remembered app→layout associations are device-specific and are not.
 *   2. recentlyUsedProviderModels MRU tracking (ProviderModelTrackingRepository) —
 *      transient usage history, not a setting.
 *   3. recentEmojis — managed automatically by the emoji keyboard; it lives in
 *      [AppearanceSettings] for runtime use but is no longer a ConfigField, so
 *      it is neither prompted, exported, nor imported.
 *   4. The built-in "Configuration" voice mode — device-local machinery rather
 *      than user content. The voiceModes list round-trips, but this entry is
 *      treated as ephemeral/built-in.
 */
data class ConfigSnapshot(
    val api: ApiSettings,
    val appearance: AppearanceSettings,
    val voiceModes: List<VoiceMode>,
    val selectedModeId: String,
) {
    /**
     * Copy with all secret material blanked: per-provider API keys on both
     * the ASR and LLM side. Anything rendered into an LLM prompt or export
     * document MUST come from a scrubbed snapshot.
     */
    fun scrubbed(): ConfigSnapshot = copy(
        api = api.copy(
            apiKeys = emptyMap(),
            llmConfig = api.llmConfig.copy(
                apiKey = "",
                apiKeys = emptyMap(),
            ),
        ),
    )
}

/**
 * Builds [ConfigSnapshot]s from the live repositories.
 *
 * [current] (the default) scrubs all secret material — per-provider API keys
 * on both the ASR and LLM side — so a scrubbed snapshot can be embedded in an
 * LLM prompt or exported without ever leaking credentials. This is defense in
 * depth: the [ConfigSchema] registry never exposes key paths in the first
 * place, but scrubbing at construction guarantees nothing can leak even
 * through opaque serialization.
 *
 * [currentUnscrubbed] is for the persistence path only ([ConfigPatchApplier]):
 * folding changes onto a scrubbed snapshot and saving it would wipe the
 * user's stored API keys.
 */
@Singleton
class ConfigSnapshotProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun current(): ConfigSnapshot = currentUnscrubbed().scrubbed()

    suspend fun currentUnscrubbed(): ConfigSnapshot = ConfigSnapshot(
        api = settingsRepository.apiSettings.first(),
        appearance = settingsRepository.appearanceSettings.first(),
        voiceModes = settingsRepository.voiceModes.first(),
        selectedModeId = settingsRepository.selectedMode.first(),
    )
}
