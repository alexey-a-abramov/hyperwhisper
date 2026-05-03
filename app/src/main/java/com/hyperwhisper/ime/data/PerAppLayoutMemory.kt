package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.perAppLayoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_per_app_layout"
)

/**
 * Per-app keyboard layout memory.
 *
 * Each known foreground package gets its own remembered [KeyboardInputMode],
 * keyed under "pkg_<packageName>". When the IME re-attaches inside that app
 * the service queries [recall] and dispatches the result through the keyboard
 * view model so the layout switches before the user sees the surface.
 *
 * Stored as individual `stringPreferencesKey` entries (rather than a packed
 * map) so DataStore can update single keys atomically without round-tripping
 * the whole table on every write.
 *
 * The repository never throws to its caller — both [recall] and [remember]
 * are infallible; on disk error they no-op (recall returns null, remember
 * silently drops). This matches the rest of the IME: a layout-restore failure
 * must never block the keyboard from coming up.
 */
@Singleton
class PerAppLayoutMemory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.perAppLayoutDataStore

    /**
     * Persist that [packageName] last used [mode]. Caller is responsible for
     * gating on the user's master toggle — this method always writes.
     */
    suspend fun remember(packageName: String, mode: KeyboardInputMode) {
        if (packageName.isBlank()) return
        try {
            dataStore.edit { prefs ->
                prefs[keyFor(packageName)] = mode.name
            }
        } catch (_: Throwable) {
            // Best-effort write; never let a per-app memory failure surface
            // up into the IME path.
        }
    }

    /**
     * Return the last [KeyboardInputMode] used in [packageName], or null if
     * we've never seen this app. Null is meaningful: callers use it to fall
     * back to the global `lastKeyboardInputMode`, so we must NOT substitute
     * a default here.
     *
     * Legacy enum values that no longer have UI representation are normalized
     * via [KeyboardInputMode.normalize] so a stored SPECIAL_CHARS surfaces as
     * QWERTY etc.
     */
    suspend fun recall(packageName: String): KeyboardInputMode? {
        if (packageName.isBlank()) return null
        return try {
            val prefs = dataStore.data.first()
            val raw = prefs[keyFor(packageName)] ?: return null
            try {
                KeyboardInputMode.valueOf(raw).normalize()
            } catch (_: IllegalArgumentException) {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Wipe all remembered per-app layouts. Not yet wired to UI — exposed for
     * a future "Forget all apps" affordance and for tests.
     */
    suspend fun clearAll() {
        try {
            dataStore.edit { it.clear() }
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun keyFor(packageName: String) = stringPreferencesKey("pkg_$packageName")
}
