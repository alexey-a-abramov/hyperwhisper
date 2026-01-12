package com.hyperwhisper.ime.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component responsible for downloading APK files
 * Handles HTTP downloads with progress tracking
 */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
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
}
