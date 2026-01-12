package com.hyperwhisper.ime.update

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component responsible for checking updates from GitHub Releases API
 * Fetches latest release information and parses GitHub API responses
 */
@Singleton
class GitHubUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GitHubUpdateChecker"

        // GitHub repository info
        private const val GITHUB_OWNER = "alexey-a-abramov"
        private const val GITHUB_REPO = "hyperwhisper"

        // GitHub Releases API URL
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * Fetch update info from GitHub Releases API
     * API: https://api.github.com/repos/{owner}/{repo}/releases/latest
     */
    fun fetchFromGitHubReleases(): UpdateInfo? {
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
}
