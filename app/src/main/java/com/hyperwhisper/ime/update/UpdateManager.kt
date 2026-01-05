package com.hyperwhisper.ime.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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
    val fileSize: Long = 0L
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

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val SKIPPED_VERSION_KEY = "skipped_update_version"
        private const val UPDATE_CHECK_URL = "https://raw.githubusercontent.com/yourusername/hyperwhisper/refs/heads/main/latest-version.json"

        // For testing: local file path or test URL
        private const val TEST_UPDATE_URL = "file:///data/local/tmp/hyperwhisper-update.json"
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
     * Mark a version as skipped (don't prompt again for this version)
     */
    fun skipVersion(versionCode: Int) {
        prefs.edit().putInt(SKIPPED_VERSION_KEY, versionCode).apply()
    }

    /**
     * Clear skipped version (e.g., after updating)
     */
    fun clearSkippedVersion() {
        prefs.edit().remove(SKIPPED_VERSION_KEY).apply()
    }

    /**
     * Check for updates from remote URL
     * Returns NoUpdateAvailable, UpdateAvailable, or Error
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val (currentVersionCode, currentVersionName) = getCurrentVersion()

            // Try to fetch update info from server
            val updateInfo = fetchUpdateInfo() ?: return@withContext UpdateCheckResult.NoUpdateAvailable

            // Check if update is newer
            if (updateInfo.versionCode <= currentVersionCode) {
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            // Check if user skipped this version
            if (isVersionSkipped(updateInfo.versionCode)) {
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            UpdateCheckResult.UpdateAvailable(updateInfo)
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Fetch update info from server
     * TODO: Replace with actual GitHub URL or other source
     */
    private suspend fun fetchUpdateInfo(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(UPDATE_CHECK_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return null
            }

            val body = response.body?.string() ?: return null

            // Parse JSON response
            // Expected format: {"versionCode": 54, "versionName": "1.0.1", "apkUrl": "...", "releaseNotes": "..."}
            parseUpdateInfo(body)
        } catch (e: Exception) {
            // Try local test file for development
            tryLocalUpdateFile()
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
     * Install the downloaded APK
     */
    fun installApk() {
        try {
            val apkFile = File(context.getExternalFilesDir(null), "update.apk")
            if (!apkFile.exists()) {
                return
            }

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
            e.printStackTrace()
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
