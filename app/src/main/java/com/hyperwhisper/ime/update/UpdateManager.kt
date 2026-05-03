package com.hyperwhisper.ime.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hyperwhisper.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updatePrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_update_prefs",
)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val apkProber: ApkProber,
    private val gitHubUpdateChecker: GitHubUpdateChecker,
    private val apkDownloader: ApkDownloader,
    private val apkInstaller: ApkInstaller
) {
    companion object {
        private const val TAG = "UpdateManager"
        private val SKIPPED_VERSION_KEY = intPreferencesKey("skipped_update_version")
        private val SKIPPED_BUILD_TIMESTAMP_KEY = longPreferencesKey("skipped_build_timestamp")
        private val MIGRATED_FROM_SHARED_PREFS = booleanPreferencesKey("migrated_from_shared_prefs_v1")

        // Fallback: raw JSON file in repo (for testing or if API rate limited)
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/alexey-a-abramov/hyperwhisper/main/latest-version.json"
    }

    /**
     * In-memory snapshot of the persisted skip state, kept in sync with
     * DataStore by [scope]'s collector. The public API stays synchronous —
     * callers were already non-suspend (UI onClick handlers, ApkProber
     * lambdas) and rewriting them all just to thread `suspend` would have
     * been invasive without a real benefit.
     */
    private data class SkipState(val skippedVersion: Int = -1, val skippedBuildTimestamp: Long = -1L)

    private val dataStore = context.updatePrefsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(SkipState())

    init {
        scope.launch {
            migrateFromSharedPreferencesIfNeeded()
            dataStore.data.map { prefs ->
                SkipState(
                    skippedVersion = prefs[SKIPPED_VERSION_KEY] ?: -1,
                    skippedBuildTimestamp = prefs[SKIPPED_BUILD_TIMESTAMP_KEY] ?: -1L,
                )
            }.collect { state.value = it }
        }
    }

    private suspend fun migrateFromSharedPreferencesIfNeeded() {
        val current = dataStore.data.first()
        if (current[MIGRATED_FROM_SHARED_PREFS] == true) return

        val legacy = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val legacyVersion = legacy.getInt("skipped_update_version", -1)
        val legacyBuildTimestamp = legacy.getLong("skipped_build_timestamp", -1L)

        dataStore.edit { prefs ->
            if (legacyVersion != -1) prefs[SKIPPED_VERSION_KEY] = legacyVersion
            if (legacyBuildTimestamp != -1L) prefs[SKIPPED_BUILD_TIMESTAMP_KEY] = legacyBuildTimestamp
            prefs[MIGRATED_FROM_SHARED_PREFS] = true
        }
        // Best-effort cleanup of the old SharedPreferences file.
        runCatching { legacy.edit().clear().apply() }
    }

    /**
     * Get current app version
     */
    fun getCurrentVersion(): Pair<Int, String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return Pair(packageInfo.versionCode.toInt(), packageInfo.versionName)
    }

    /** Check if a specific version was skipped by user. */
    fun isVersionSkipped(versionCode: Int): Boolean = state.value.skippedVersion == versionCode

    /** Check if a specific build timestamp was skipped by user. */
    fun isBuildTimestampSkipped(buildTimestamp: Long): Boolean =
        state.value.skippedBuildTimestamp == buildTimestamp

    /** Mark a version as skipped (don't prompt again for this version). */
    fun skipVersion(versionCode: Int) {
        scope.launch {
            dataStore.edit { it[SKIPPED_VERSION_KEY] = versionCode }
        }
    }

    /** Mark a build timestamp as skipped (don't prompt again for this build). */
    fun skipBuildTimestamp(buildTimestamp: Long) {
        scope.launch {
            dataStore.edit { it[SKIPPED_BUILD_TIMESTAMP_KEY] = buildTimestamp }
        }
    }

    /** Clear skipped version (e.g., after updating). */
    fun clearSkippedVersion() {
        scope.launch {
            dataStore.edit { prefs ->
                prefs.remove(SKIPPED_VERSION_KEY)
                prefs.remove(SKIPPED_BUILD_TIMESTAMP_KEY)
            }
        }
    }

    /**
     * Get the build date string (human-readable)
     */
    fun getBuildDate(): String {
        return try {
            BuildConfig.BUILD_DATE
        } catch (e: Exception) {
            UpdateFormatters.formatTimestamp(BuildConfig.BUILD_TIMESTAMP)
        }
    }

    /**
     * Get the installed APK path
     */
    fun getInstalledApkPath(): String {
        return try {
            context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get detailed update probe information for display
     * This checks all APK locations and returns detailed status for each
     */
    fun getUpdateProbeDetails(): UpdateProbeDetails {
        val (currentVersionCode, currentVersionName) = getCurrentVersion()
        val currentBuildTimestamp = BuildConfig.BUILD_TIMESTAMP
        val installedApkPath = getInstalledApkPath()

        // Get local APK probe details
        val probeDetails = apkProber.getUpdateProbeDetails(
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            currentBuildTimestamp = currentBuildTimestamp,
            installedApkPath = installedApkPath,
            isBuildTimestampSkipped = ::isBuildTimestampSkipped
        )

        // Also check GitHub Releases
        var githubReleaseChecked = false
        var githubReleaseVersion: String? = null
        var githubReleaseUrl: String? = null
        var githubReleaseError: String? = null
        var updateAvailable = probeDetails.updateAvailable
        var updateSource = probeDetails.updateSource

        try {
            val githubUpdate = gitHubUpdateChecker.fetchFromGitHubReleases()
            githubReleaseChecked = true
            if (githubUpdate != null) {
                githubReleaseVersion = githubUpdate.versionName
                githubReleaseUrl = githubUpdate.apkUrl
                // Check if GitHub version is newer
                if (githubUpdate.versionCode > currentVersionCode && !updateAvailable) {
                    updateAvailable = true
                    updateSource = "GitHub Release v${githubUpdate.versionName}"
                }
            }
        } catch (e: Exception) {
            githubReleaseChecked = true
            githubReleaseError = e.message
        }

        return probeDetails.copy(
            updateAvailable = updateAvailable,
            updateSource = updateSource,
            githubReleaseChecked = githubReleaseChecked,
            githubReleaseVersion = githubReleaseVersion,
            githubReleaseUrl = githubReleaseUrl,
            githubReleaseError = githubReleaseError
        )
    }


    /**
     * Check for updates from remote URL or local APK files
     * Returns NoUpdateAvailable, UpdateAvailable, or Error
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val (currentVersionCode, currentVersionName) = getCurrentVersion()
            val currentBuildTimestamp = BuildConfig.BUILD_TIMESTAMP

            Log.i(TAG, "=== Update Check ===")
            Log.i(TAG, "  Installed: $currentVersionName (code: $currentVersionCode)")
            Log.i(TAG, "  Built: ${getBuildDate()}")
            Log.d(TAG, "  Timestamp: $currentBuildTimestamp")

            // First, check for local APK files that are newer
            val localUpdate = apkProber.checkLocalApkUpdates(
                currentBuildTimestamp = currentBuildTimestamp,
                isBuildTimestampSkipped = ::isBuildTimestampSkipped
            )
            if (localUpdate != null) {
                Log.d(TAG, "  Found local APK update")
                return@withContext localUpdate
            }

            // Then try to fetch update info from server
            val updateInfo = fetchUpdateInfo()
            if (updateInfo == null) {
                Log.d(TAG, "  No remote update info found")
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            Log.d(TAG, "  Remote update info: ${updateInfo.versionName} (${updateInfo.versionCode})")

            // Check if update is newer by version code
            if (updateInfo.versionCode <= currentVersionCode) {
                Log.d(TAG, "  Remote version is not newer")
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            // Check if user skipped this version
            if (isVersionSkipped(updateInfo.versionCode)) {
                Log.d(TAG, "  User skipped this version")
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            Log.d(TAG, "  Update available!")
            UpdateCheckResult.UpdateAvailable(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Fetch update info from GitHub Releases API or fallback sources
     */
    private suspend fun fetchUpdateInfo(): UpdateInfo? {
        // Try GitHub Releases API first
        val githubUpdate = gitHubUpdateChecker.fetchFromGitHubReleases()
        if (githubUpdate != null) {
            Log.d(TAG, "  Got update info from GitHub Releases API")
            return githubUpdate
        }

        // Fallback to raw JSON file
        val jsonUpdate = fetchFromJsonFile()
        if (jsonUpdate != null) {
            Log.d(TAG, "  Got update info from JSON file")
            return jsonUpdate
        }

        // Try local test file for development
        return tryLocalUpdateFile()
    }

    /**
     * Fetch update info from raw JSON file
     */
    private fun fetchFromJsonFile(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(UPDATE_CHECK_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return null
            }

            val body = response.body?.string() ?: return null
            parseUpdateInfo(body)
        } catch (e: Exception) {
            Log.w(TAG, "  Failed to fetch from JSON file: ${e.message}")
            null
        }
    }

    /**
     * Try to load update info from local test file
     */
    private suspend fun tryLocalUpdateFile(): UpdateInfo? {
        return try {
            // Try multiple locations for the test file
            val locations = listOf(
                "/data/local/tmp/hyperwhisper-update.json",
                "/storage/emulated/0/Download/hyperwhisper-update.json",
                "/storage/emulated/0/Documents/hyperwhisper-update.json"
            )

            for (path in locations) {
                val testFile = File(path)
                if (testFile.exists() && testFile.canRead()) {
                    val json = testFile.readText()
                    val info = parseUpdateInfo(json)
                    if (info != null) {
                        Log.d(TAG, "Found update info from: $path")
                        return info
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local update file", e)
            null
        }
    }

    /**
     * Parse update info from JSON
     * Simple manual parsing to avoid extra dependencies
     */
    private fun parseUpdateInfo(json: String): UpdateInfo? {
        return try {
            val versionCode = Regex("\"versionCode\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: return null
            val versionName = Regex("\"versionName\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: return null
            val apkUrl = Regex("\"apkUrl\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: return null
            val releaseNotes = Regex("\"releaseNotes\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
            val fileSize = Regex("\"fileSize\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLong() ?: 0L

            UpdateInfo(versionCode, versionName, apkUrl, releaseNotes, fileSize)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download APK from URL
     * Uses callback for progress updates
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = apkDownloader.downloadApk(url, onProgress)

    /**
     * Check if a path is a local file (not a URL)
     */
    fun isLocalPath(path: String): Boolean = apkDownloader.isLocalPath(path)

    /**
     * Install APK from path (local file or downloaded update.apk)
     * @param apkPath Optional path to APK file. If null, uses the downloaded update.apk
     */
    fun installApk(apkPath: String? = null) = apkInstaller.installApk(apkPath)

    /**
     * Delete downloaded APK to free space
     */
    fun cleanupApk() = apkInstaller.cleanupApk()
}
