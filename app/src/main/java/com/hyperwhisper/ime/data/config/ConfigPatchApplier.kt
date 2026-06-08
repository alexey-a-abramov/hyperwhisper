package com.hyperwhisper.data.config

import android.util.Log
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of persisting a confirmed patch. */
data class ApplyResult(
    val success: Boolean,
    val appliedCount: Int,
    val errorMessage: String? = null,
)

/**
 * Persists a user-confirmed [PendingConfigPatch].
 *
 * Changes are folded onto the UNSCRUBBED current snapshot (the scrubbed one
 * used for prompts/diffs has API keys blanked — persisting it would wipe
 * stored credentials), then only the roots that actually changed are saved,
 * each as a single DataStore edit.
 */
@Singleton
class ConfigPatchApplier @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val snapshotProvider: ConfigSnapshotProvider,
) {
    companion object {
        private const val TAG = "ConfigPatchApplier"
        const val KEYBOARD_MODE_PATH = "appearance.keyboardMode"

        /** Pure core: apply changes to a snapshot. Exposed for tests. */
        fun fold(snapshot: ConfigSnapshot, changes: List<ResolvedChange>): ConfigSnapshot =
            changes.fold(snapshot) { acc, change -> change.field.set(acc, change.newValue) }
    }

    /**
     * Emits when a confirmed patch changed the active keyboard mode, so the
     * live keyboard switches immediately (same path as a manual mode-pill
     * tap). Collected by KeyboardViewModel.
     */
    private val _keyboardModeRequest = MutableSharedFlow<KeyboardInputMode>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val keyboardModeRequest: SharedFlow<KeyboardInputMode> = _keyboardModeRequest

    suspend fun apply(patch: PendingConfigPatch): ApplyResult {
        if (patch.valid.isEmpty()) return ApplyResult(success = false, appliedCount = 0)

        return try {
            val before = snapshotProvider.currentUnscrubbed()
            val after = fold(before, patch.valid)

            if (after.api != before.api) {
                settingsRepository.saveApiSettings(after.api)
            }
            if (after.appearance != before.appearance) {
                settingsRepository.saveAppearanceSettings(after.appearance)
            }
            if (after.voiceModes != before.voiceModes) {
                settingsRepository.saveVoiceModes(after.voiceModes)
            }
            if (after.selectedModeId != before.selectedModeId) {
                settingsRepository.setSelectedMode(after.selectedModeId)
            }

            // Live-switch the keyboard if the active mode was among the changes.
            patch.valid.firstOrNull { it.field.path == KEYBOARD_MODE_PATH }?.let { change ->
                runCatching { KeyboardInputMode.valueOf(change.newValue as String) }
                    .getOrNull()
                    ?.let { _keyboardModeRequest.emit(it) }
            }

            Log.d(TAG, "Applied ${patch.valid.size} config change(s)")
            ApplyResult(success = true, appliedCount = patch.valid.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply config patch", e)
            ApplyResult(success = false, appliedCount = 0, errorMessage = e.message)
        }
    }
}
