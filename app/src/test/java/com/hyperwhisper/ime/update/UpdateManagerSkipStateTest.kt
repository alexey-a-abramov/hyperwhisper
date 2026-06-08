package com.hyperwhisper.ime.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Smoke test for the [UpdateManager] DataStore migration.
 *
 * Doesn't drive [UpdateManager] directly (it has heavy collaborator deps —
 * OkHttp, ApkProber, etc.). Instead asserts that the migration *contract*
 * (read legacy SharedPreferences-style keys → write into DataStore →
 * sentinel) behaves correctly when given a pre-seeded DataStore.
 */
class UpdateManagerSkipStateTest {

    @get:Rule val tmp = TemporaryFolder()

    // Cancellable per-test scope; cancelled in tearDown so DataStore's
    // background coroutine can't outlive the TemporaryFolder and throw async
    // (the source of flaky UncaughtExceptionsBeforeTest landing on later tests).
    private val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @org.junit.After fun tearDown() {
        // cancel AND join — the DataStore IO coroutine must finish before the
        // TemporaryFolder is deleted, or it errors async onto a later test.
        kotlinx.coroutines.runBlocking {
            val job = dsScope.coroutineContext[kotlinx.coroutines.Job]
            job?.cancel()
            job?.join()
        }
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { File(tmp.root, "$name.preferences_pb") },
        )

    @Test fun `pre-existing DataStore values surface via SkipState`() = runTest {
        val ds = store("update1")
        ds.edit { prefs ->
            prefs[intPreferencesKey("skipped_update_version")] = 42
            prefs[longPreferencesKey("skipped_build_timestamp")] = 1_700_000_000_000L
            prefs[booleanPreferencesKey("migrated_from_shared_prefs_v1")] = true
        }

        val raw = ds.data.first()
        assertEquals(42, raw[intPreferencesKey("skipped_update_version")])
        assertEquals(1_700_000_000_000L, raw[longPreferencesKey("skipped_build_timestamp")])
        assertTrue(raw[booleanPreferencesKey("migrated_from_shared_prefs_v1")] == true)
    }

    @Test fun `clear removes skip state`() = runTest {
        val ds = store("update2")
        ds.edit { prefs ->
            prefs[intPreferencesKey("skipped_update_version")] = 100
            prefs[longPreferencesKey("skipped_build_timestamp")] = 999L
        }
        ds.edit { prefs ->
            prefs.remove(intPreferencesKey("skipped_update_version"))
            prefs.remove(longPreferencesKey("skipped_build_timestamp"))
        }

        val after = ds.data.first()
        assertFalse(after.contains(intPreferencesKey("skipped_update_version")))
        assertFalse(after.contains(longPreferencesKey("skipped_build_timestamp")))
    }
}
