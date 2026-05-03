package com.hyperwhisper.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.hyperwhisper.BuildConfig
import java.io.File

/**
 * Central gate for dev-only file logging.
 *
 * Two compile-time gates AND-ed together:
 *   1. BuildConfig.DEBUG       — release builds: no-op (R8 dead-code-eliminates)
 *   2. BuildConfig.DEV_LOGS_ENABLED — opt-in via Gradle property `-PdevLogs=true`
 *
 * Optional runtime escape hatch: presence of /sdcard/apk-logs/HyperWhisper/.enable-dev-logs
 * (only checked in debug builds; a way to flip logs on without rebuilding).
 *
 * All public API is infallible — callers never see exceptions from this class.
 */
object DevLogConfig {
    private const val TAG = "DevLogConfig"
    // Layout: /sdcard/apk-logs/<AppName>/  — one shared root for all apps
    // following this convention, with a per-app subfolder.
    private const val ROOT_DIR_NAME = "apk-logs"
    private const val APP_DIR_NAME = "HyperWhisper"
    private const val MAGIC_FILE_NAME = ".enable-dev-logs"

    @Volatile private var enabledCache: Boolean? = null

    /** Hard gate. Cheap; safe to call on hot paths. */
    fun isEnabled(): Boolean {
        if (!BuildConfig.DEBUG) return false
        if (BuildConfig.DEV_LOGS_ENABLED) return true
        // Runtime opt-in via magic file (cached after first check)
        enabledCache?.let { return it }
        val computed = try {
            val root = Environment.getExternalStorageDirectory()
            File(root, "$ROOT_DIR_NAME/$APP_DIR_NAME/$MAGIC_FILE_NAME").exists()
        } catch (t: Throwable) {
            false
        }
        enabledCache = computed
        return computed
    }

    /**
     * Returns the directory that should hold log files, or null if logs are
     * disabled OR the external location isn't writable. Never throws.
     */
    fun resolveLogsDir(fallbackContext: Context?): File? {
        if (!isEnabled()) return null
        // Prefer external location (Termux-readable); fall back to app's filesDir.
        val external = tryExternalLogsDir()
        if (external != null) return external
        return tryInternalLogsDir(fallbackContext)
    }

    /** Just the external path probe, no fallback — exposed for diagnostics. */
    fun tryExternalLogsDir(): File? = try {
        val canManage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        if (!canManage) {
            Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted; external logs unavailable")
            null
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), "$ROOT_DIR_NAME/$APP_DIR_NAME")
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Could not create external logs dir: ${dir.absolutePath}")
                null
            } else dir
        }
    } catch (t: Throwable) {
        Log.w(TAG, "External logs dir probe failed: ${t.message}")
        null
    }

    private fun tryInternalLogsDir(context: Context?): File? = try {
        val base = context?.filesDir ?: return null
        val dir = File(base, "logs")
        if (!dir.exists()) dir.mkdirs()
        dir
    } catch (t: Throwable) {
        Log.w(TAG, "Internal logs dir probe failed: ${t.message}")
        null
    }

    /**
     * Keep at most [keep] files matching [prefix] in [dir], deleting oldest first.
     * Best-effort; never throws.
     */
    fun pruneOldFiles(dir: File, prefix: String, keep: Int) {
        try {
            val files = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) } ?: return
            if (files.size <= keep) return
            files.sortedBy { it.lastModified() }
                .take(files.size - keep)
                .forEach { runCatching { it.delete() } }
        } catch (t: Throwable) {
            Log.w(TAG, "Prune failed: ${t.message}")
        }
    }
}
