package com.hyperwhisper.ime.update

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shared host for the update dialog flow.
 *
 * Renders [UpdateDialog] when [updateInfo] is non-null, wired to download +
 * install via [updateManager]. Lives outside any single activity so both
 * SettingsActivity and AboutActivity can surface the same flow without
 * duplicating ~70 lines of glue.
 */
@Composable
fun UpdateDialogHost(
    updateInfo: UpdateInfo?,
    updateManager: UpdateManager,
    onDismiss: () -> Unit,
) {
    val info = updateInfo ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = updateManager.getCurrentVersion().second
    val isLocalBuild = info.apkUrl.startsWith("/") || info.buildTimestamp > 0

    UpdateDialog(
        updateInfo = info,
        currentVersion = currentVersion,
        onDismiss = onDismiss,
        onSkip = {
            if (info.buildTimestamp > 0) {
                updateManager.skipBuildTimestamp(info.buildTimestamp)
            } else {
                updateManager.skipVersion(info.versionCode)
            }
            onDismiss()
        },
        onUpdate = { progressCallback, onComplete, onError ->
            scope.launch {
                try {
                    if (isLocalBuild && updateManager.isLocalPath(info.apkUrl)) {
                        val sourceFile = File(info.apkUrl)
                        if (!sourceFile.exists()) {
                            onError("Local APK file not found: ${info.apkUrl}")
                            Toast.makeText(
                                context,
                                "Local APK not found: ${info.apkUrl}",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        progressCallback(100)
                        onComplete()
                        updateManager.installApk(info.apkUrl)
                    } else {
                        when (val result = updateManager.downloadApk(info.apkUrl, progressCallback)) {
                            is DownloadResult.Success -> {
                                onComplete()
                                updateManager.installApk()
                            }
                            is DownloadResult.Error -> {
                                onError(result.message)
                                Toast.makeText(
                                    context,
                                    "Download failed: ${result.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    onError(e.message ?: "Unknown error")
                    Toast.makeText(
                        context,
                        "Update failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )
}
