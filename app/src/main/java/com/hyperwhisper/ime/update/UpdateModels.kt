package com.hyperwhisper.ime.update

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
