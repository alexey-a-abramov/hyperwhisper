package com.hyperwhisper.ime.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component responsible for installing APK files and cleanup
 * Handles APK installation intents and downloaded file cleanup
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apkDownloader: ApkDownloader
) {
    companion object {
        private const val TAG = "ApkInstaller"
    }

    /**
     * Install APK from path (local file or downloaded update.apk)
     * @param apkPath Optional path to APK file. If null, uses the downloaded update.apk
     */
    fun installApk(apkPath: String? = null) {
        try {
            val apkFile = if (apkPath != null && apkDownloader.isLocalPath(apkPath)) {
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
