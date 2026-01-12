package com.hyperwhisper.ime.update

import android.content.Context
import android.os.Build
import android.util.Log
import com.hyperwhisper.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component responsible for probing local file system for APK files
 * Checks various locations for newer builds and analyzes APK metadata
 */
@Singleton
class ApkProber @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ApkProber"

        // Minimum time difference (in ms) to consider an APK as newer
        // This prevents false positives from minor timestamp variations
        private const val MIN_UPDATE_THRESHOLD_MS = 60_000L // 1 minute

        // Local APK locations to check for updates (in order of priority)
        val LOCAL_APK_LOCATIONS = listOf(
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

    /**
     * Get detailed update probe information for display
     * This checks all APK locations and returns detailed status for each
     */
    fun getUpdateProbeDetails(
        currentVersionCode: Int,
        currentVersionName: String,
        currentBuildTimestamp: Long,
        installedApkPath: String,
        isBuildTimestampSkipped: (Long) -> Boolean
    ): UpdateProbeDetails {
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
                val displayPath = UpdateFormatters.shortenPath(apkPath)

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
                    fileModifiedDate = UpdateFormatters.formatTimestamp(fileModifiedTime),
                    fileSize = apkFile.length(),
                    fileSizeFormatted = UpdateFormatters.formatFileSize(apkFile.length()),
                    isNewer = isNewer,
                    timeDifferenceMs = timeDifference,
                    timeDifferenceFormatted = UpdateFormatters.formatTimeDifference(timeDifference),
                    errorMessage = if (isSkipped) "Skipped by user" else null
                ))
            } catch (e: Exception) {
                probeResults.add(ApkProbeResult(
                    path = apkPath,
                    displayPath = UpdateFormatters.shortenPath(apkPath),
                    exists = false,
                    readable = false,
                    errorMessage = e.message
                ))
            }
        }

        return UpdateProbeDetails(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            currentBuildTimestamp = currentBuildTimestamp,
            currentBuildDate = formatBuildDate(currentBuildTimestamp),
            installedApkPath = installedApkPath,
            probeResults = probeResults,
            updateAvailable = updateAvailable,
            updateSource = updateSource,
            githubReleaseChecked = false,
            githubReleaseVersion = null,
            githubReleaseUrl = null,
            githubReleaseError = null
        )
    }

    /**
     * Check for local APK files that are newer than the installed app
     * Returns UpdateAvailable if found, null otherwise
     *
     * Uses file modification time compared against the embedded BUILD_TIMESTAMP.
     * A minimum threshold ensures minor timestamp variations don't trigger false updates.
     */
    fun checkLocalApkUpdates(
        currentBuildTimestamp: Long,
        isBuildTimestampSkipped: (Long) -> Boolean
    ): UpdateCheckResult? {
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
                Log.d(TAG, "    File modified: ${UpdateFormatters.formatTimestamp(fileModifiedTime)}")
                Log.d(TAG, "    App built:     ${UpdateFormatters.formatTimestamp(currentBuildTimestamp)}")
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
     * Get the build date string (human-readable)
     */
    private fun formatBuildDate(buildTimestamp: Long): String {
        return try {
            BuildConfig.BUILD_DATE
        } catch (e: Exception) {
            UpdateFormatters.formatTimestamp(buildTimestamp)
        }
    }
}
