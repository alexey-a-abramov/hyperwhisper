package com.hyperwhisper.ime.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.hyperwhisper.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

/**
 * Data class representing update information from server
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val fileSize: Long = 0L,
    val buildTimestamp: Long = 0L
)

/**
 * Result of update check
 */
sealed class UpdateCheckResult {
    object NoUpdateAvailable : UpdateCheckResult()
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Result of download operation
 */
sealed class DownloadResult {
    object Success : DownloadResult()
    data class Progress(val percent: Int) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

/**
 * Detailed probe result for a single APK location
 */
data class ApkProbeResult(
    val path: String,
    val displayPath: String, // Shortened path for display
    val exists: Boolean,
    val readable: Boolean,
    val fileModifiedTime: Long = 0L,
    val fileModifiedDate: String = "",
    val fileSize: Long = 0L,
    val fileSizeFormatted: String = "",
    val isNewer: Boolean = false,
    val timeDifferenceMs: Long = 0L,
    val timeDifferenceFormatted: String = "",
    val errorMessage: String? = null
)

/**
 * Overall update probe details
 */
data class UpdateProbeDetails(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val currentBuildTimestamp: Long,
    val currentBuildDate: String,
    val installedApkPath: String,
    val probeResults: List<ApkProbeResult>,
    val updateAvailable: Boolean,
    val updateSource: String? = null,
    // GitHub release info
    val githubReleaseChecked: Boolean = false,
    val githubReleaseVersion: String? = null,
    val githubReleaseUrl: String? = null,
    val githubReleaseError: String? = null
)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val SKIPPED_VERSION_KEY = "skipped_update_version"
        private const val SKIPPED_BUILD_TIMESTAMP_KEY = "skipped_build_timestamp"

        // GitHub repository info
        private const val GITHUB_OWNER = "alexey-a-abramov"
        private const val GITHUB_REPO = "hyperwhisper"

        // GitHub Releases API URL
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        // Fallback: raw JSON file in repo (for testing or if API rate limited)
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/latest-version.json"

        // Minimum time difference (in ms) to consider an APK as newer
        // This prevents false positives from minor timestamp variations
        private const val MIN_UPDATE_THRESHOLD_MS = 60_000L // 1 minute

        // Local APK locations to check for updates (in order of priority)
        private val LOCAL_APK_LOCATIONS = listOf(
            // Primary: SD card standard location for easy access
            "/storage/emulated/0/HyperWhisper/app-debug.apk",
            // Termux project builds folder (auto-copied by gradle)
            "/data/data/com.termux/files/home/projects/hyperwhisper/builds/app-debug.apk",
            // Direct gradle output
            "/data/data/com.termux/files/home/projects/hyperwhisper/app/build/outputs/apk/debug/app-debug.apk",
            // Download folder alternatives
            "/storage/emulated/0/Download/hyperwhisper-debug.apk",
            "/storage/emulated/0/Download/app-debug.apk"
        )
    }

    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

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

    /**
     * Check if a specific version was skipped by user
     */
    fun isVersionSkipped(versionCode: Int): Boolean {
        return prefs.getInt(SKIPPED_VERSION_KEY, -1) == versionCode
    }

    /**
     * Check if a specific build timestamp was skipped by user
     */
    fun isBuildTimestampSkipped(buildTimestamp: Long): Boolean {
        return prefs.getLong(SKIPPED_BUILD_TIMESTAMP_KEY, -1) == buildTimestamp
    }

    /**
     * Mark a version as skipped (don't prompt again for this version)
     */
    fun skipVersion(versionCode: Int) {
        prefs.edit().putInt(SKIPPED_VERSION_KEY, versionCode).apply()
    }

    /**
     * Mark a build timestamp as skipped (don't prompt again for this build)
     */
    fun skipBuildTimestamp(buildTimestamp: Long) {
        prefs.edit().putLong(SKIPPED_BUILD_TIMESTAMP_KEY, buildTimestamp).apply()
    }

    /**
     * Clear skipped version (e.g., after updating)
     */
    fun clearSkippedVersion() {
        prefs.edit().remove(SKIPPED_VERSION_KEY).apply()
        prefs.edit().remove(SKIPPED_BUILD_TIMESTAMP_KEY).apply()
    }

    /**
     * Get the build date string (human-readable)
     */
    fun getBuildDate(): String {
        return try {
            BuildConfig.BUILD_DATE
        } catch (e: Exception) {
            formatTimestamp(BuildConfig.BUILD_TIMESTAMP)
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

        val probeResults = mutableListOf<ApkProbeResult>()
        var updateAvailable = false
        var updateSource: String? = null

        // Also add alternate path representations that Android might use
        val allPaths = LOCAL_APK_LOCATIONS.toMutableList()

        // Add common Android path aliases
        LOCAL_APK_LOCATIONS.forEach { path ->
            if (path.startsWith("/storage/emulated/0")) {
                allPaths.add(path.replace("/storage/emulated/0", "/sdcard"))
            } else if (path.startsWith("/sdcard")) {
                allPaths.add(path.replace("/sdcard", "/storage/emulated/0"))
            }
        }

        for (apkPath in allPaths.distinct()) {
            try {
                val apkFile = File(apkPath)
                val displayPath = shortenPath(apkPath)

                if (!apkFile.exists()) {
                    probeResults.add(ApkProbeResult(
                        path = apkPath,
                        displayPath = displayPath,
                        exists = false,
                        readable = false
                    ))
                    continue
                }

                if (!apkFile.canRead()) {
                    probeResults.add(ApkProbeResult(
                        path = apkPath,
                        displayPath = displayPath,
                        exists = true,
                        readable = false,
                        errorMessage = "Not readable"
                    ))
                    continue
                }

                val fileModifiedTime = apkFile.lastModified()
                val timeDifference = fileModifiedTime - currentBuildTimestamp
                val isNewer = timeDifference > MIN_UPDATE_THRESHOLD_MS
                val isSkipped = isBuildTimestampSkipped(fileModifiedTime)

                if (isNewer && !isSkipped && !updateAvailable) {
                    updateAvailable = true
                    updateSource = apkPath
                }

                probeResults.add(ApkProbeResult(
                    path = apkPath,
                    displayPath = displayPath,
                    exists = true,
                    readable = true,
                    fileModifiedTime = fileModifiedTime,
                    fileModifiedDate = formatTimestamp(fileModifiedTime),
                    fileSize = apkFile.length(),
                    fileSizeFormatted = formatFileSize(apkFile.length()),
                    isNewer = isNewer,
                    timeDifferenceMs = timeDifference,
                    timeDifferenceFormatted = formatTimeDifference(timeDifference),
                    errorMessage = if (isSkipped) "Skipped by user" else null
                ))
            } catch (e: Exception) {
                probeResults.add(ApkProbeResult(
                    path = apkPath,
                    displayPath = shortenPath(apkPath),
                    exists = false,
                    readable = false,
                    errorMessage = e.message
                ))
            }
        }

        // Also check GitHub Releases
        var githubReleaseChecked = false
        var githubReleaseVersion: String? = null
        var githubReleaseUrl: String? = null
        var githubReleaseError: String? = null

        try {
            val githubUpdate = fetchFromGitHubReleases()
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

        return UpdateProbeDetails(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            currentBuildTimestamp = currentBuildTimestamp,
            currentBuildDate = getBuildDate(),
            installedApkPath = installedApkPath,
            probeResults = probeResults,
            updateAvailable = updateAvailable,
            updateSource = updateSource,
            githubReleaseChecked = githubReleaseChecked,
            githubReleaseVersion = githubReleaseVersion,
            githubReleaseUrl = githubReleaseUrl,
            githubReleaseError = githubReleaseError
        )
    }

    /**
     * Shorten path for display (remove common prefixes)
     */
    private fun shortenPath(path: String): String {
        return path
            .replace("/data/data/com.termux/files/home/", "~/")
            .replace("/storage/emulated/0/", "/sdcard/")
    }

    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Format time difference for display
     */
    private fun formatTimeDifference(diffMs: Long): String {
        val absDiff = kotlin.math.abs(diffMs)
        val sign = if (diffMs >= 0) "+" else "-"
        return when {
            absDiff < 1000 -> "${sign}${absDiff}ms"
            absDiff < 60_000 -> "${sign}${absDiff / 1000}s"
            absDiff < 3600_000 -> "${sign}${absDiff / 60_000}m"
            absDiff < 86400_000 -> "${sign}${absDiff / 3600_000}h"
            else -> "${sign}${absDiff / 86400_000}d"
        }
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
            val localUpdate = checkLocalApkUpdates(currentBuildTimestamp)
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
     * Check for local APK files that are newer than the installed app
     * Returns UpdateAvailable if found, null otherwise
     *
     * Uses file modification time compared against the embedded BUILD_TIMESTAMP.
     * A minimum threshold ensures minor timestamp variations don't trigger false updates.
     */
    private fun checkLocalApkUpdates(currentBuildTimestamp: Long): UpdateCheckResult? {
        for (apkPath in LOCAL_APK_LOCATIONS) {
            try {
                val apkFile = File(apkPath)
                if (!apkFile.exists()) {
                    Log.d(TAG, "  APK not found: $apkPath")
                    continue
                }
                if (!apkFile.canRead()) {
                    Log.w(TAG, "  APK not readable: $apkPath")
                    continue
                }

                val fileModifiedTime = apkFile.lastModified()
                val timeDifference = fileModifiedTime - currentBuildTimestamp

                Log.d(TAG, "  Checking APK: $apkPath")
                Log.d(TAG, "    File modified: ${formatTimestamp(fileModifiedTime)}")
                Log.d(TAG, "    App built:     ${formatTimestamp(currentBuildTimestamp)}")
                Log.d(TAG, "    Difference:    ${timeDifference / 1000}s (threshold: ${MIN_UPDATE_THRESHOLD_MS / 1000}s)")

                // Check if the APK file is significantly newer than the installed app
                // Using threshold to avoid false positives from minor clock variations
                if (timeDifference > MIN_UPDATE_THRESHOLD_MS) {
                    // Check if user skipped this specific build
                    if (isBuildTimestampSkipped(fileModifiedTime)) {
                        Log.d(TAG, "    User skipped this build")
                        continue
                    }

                    // Extract version from APK's PackageInfo if possible
                    val apkVersionInfo = extractVersionFromApk(apkFile)
                    val versionName = apkVersionInfo?.second ?: extractVersionFromFilename(apkFile.name)
                    val versionCode = apkVersionInfo?.first ?: (fileModifiedTime / 1000).toInt()

                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val buildDate = dateFormat.format(Date(fileModifiedTime))

                    val updateInfo = UpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName,
                        apkUrl = apkFile.absolutePath,
                        releaseNotes = "Local build from $buildDate\nFile: ${apkFile.name}",
                        fileSize = apkFile.length(),
                        buildTimestamp = fileModifiedTime
                    )

                    Log.i(TAG, "    ✓ Update available: $versionName (built $buildDate)")
                    return UpdateCheckResult.UpdateAvailable(updateInfo)
                } else {
                    Log.d(TAG, "    APK is not newer (within threshold)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "    Error checking APK: $apkPath", e)
            }
        }
        return null
    }

    /**
     * Extract version info directly from APK file using PackageManager
     * Returns Pair(versionCode, versionName) or null if extraction fails
     */
    private fun extractVersionFromApk(apkFile: File): Pair<Int, String>? {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                0
            )
            if (packageInfo != null) {
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                Pair(versionCode, packageInfo.versionName ?: "1.$versionCode")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract version from APK: ${e.message}")
            null
        }
    }

    /**
     * Extract version from APK filename
     */
    private fun extractVersionFromFilename(filename: String): String {
        return when {
            filename.contains("debug", ignoreCase = true) -> "Debug Build"
            filename.contains("release", ignoreCase = true) -> "Release Build"
            else -> "Local Build"
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Fetch update info from GitHub Releases API or fallback sources
     */
    private suspend fun fetchUpdateInfo(): UpdateInfo? {
        // Try GitHub Releases API first
        val githubUpdate = fetchFromGitHubReleases()
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
     * Fetch update info from GitHub Releases API
     * API: https://api.github.com/repos/{owner}/{repo}/releases/latest
     */
    private fun fetchFromGitHubReleases(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "HyperWhisper-Android")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "  GitHub API response: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            parseGitHubRelease(body)
        } catch (e: Exception) {
            Log.w(TAG, "  Failed to fetch from GitHub Releases: ${e.message}")
            null
        }
    }

    /**
     * Parse GitHub Releases API response
     * Example response structure:
     * {
     *   "tag_name": "v1.96",
     *   "name": "HyperWhisper v1.96",
     *   "body": "Release notes...",
     *   "published_at": "2024-01-08T12:00:00Z",
     *   "assets": [
     *     {
     *       "name": "app-debug.apk",
     *       "browser_download_url": "https://github.com/.../app-debug.apk"
     *     }
     *   ]
     * }
     */
    private fun parseGitHubRelease(json: String): UpdateInfo? {
        return try {
            val jsonObject = org.json.JSONObject(json)

            // Parse version from tag_name (e.g., "v1.96" -> "1.96")
            val tagName = jsonObject.optString("tag_name", "")
            val versionName = tagName.removePrefix("v").removePrefix("V")
            if (versionName.isEmpty()) return null

            // Extract version code from version name (e.g., "1.96" -> 96)
            val versionCode = versionName.replace(".", "").removePrefix("1").toIntOrNull() ?: return null

            // Get release notes from body
            val releaseNotes = jsonObject.optString("body", "Bug fixes and improvements")

            // Get published timestamp
            val publishedAt = jsonObject.optString("published_at", "")
            val buildTimestamp = parseIsoTimestamp(publishedAt)

            // Find APK download URL from assets
            var apkUrl: String? = null
            val assets = jsonObject.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    // Prefer app-debug.apk or any .apk file
                    if (assetName == "app-debug.apk" || assetName.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        if (assetName == "app-debug.apk") break // Prefer this one
                    }
                }
            }

            if (apkUrl == null) {
                Log.w(TAG, "  No APK asset found in release")
                return null
            }

            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                buildTimestamp = buildTimestamp
            )
        } catch (e: Exception) {
            Log.w(TAG, "  Failed to parse GitHub release: ${e.message}")
            null
        }
    }

    /**
     * Parse ISO 8601 timestamp to milliseconds
     */
    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            if (isoString.isEmpty()) return System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
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
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadResult.Error("Download failed: ${response.code}")
            }

            val responseBody = response.body
            if (responseBody == null) {
                return@withContext DownloadResult.Error("Empty response body")
            }

            val contentLength = responseBody.contentLength()
            val apkFile = File(context.getExternalFilesDir(null), "update.apk")
            apkFile.delete()

            responseBody.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress
                        if (contentLength > 0) {
                            val percent = ((totalBytesRead * 100) / contentLength).toInt()
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }

            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    /**
     * Check if a path is a local file (not a URL)
     */
    fun isLocalPath(path: String): Boolean {
        return path.startsWith("/") && !path.startsWith("http")
    }

    /**
     * Install APK from path (local file or downloaded update.apk)
     * @param apkPath Optional path to APK file. If null, uses the downloaded update.apk
     */
    fun installApk(apkPath: String? = null) {
        try {
            val apkFile = if (apkPath != null && isLocalPath(apkPath)) {
                File(apkPath)
            } else {
                File(context.getExternalFilesDir(null), "update.apk")
            }

            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                return
            }

            Log.i(TAG, "Installing APK: ${apkFile.absolutePath}")

            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                @Suppress("DEPRECATION")
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
        }
    }

    /**
     * Delete downloaded APK to free space
     */
    fun cleanupApk() {
        try {
            val apkFile = File(context.getExternalFilesDir(null), "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
