package com.hyperwhisper.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe, dev-only trace logger.
 *
 * Writes a per-session file `trace-yyyyMMdd-HHmmss.log` into
 * `/sdcard/apk-logs/HyperWhisper/` when DevLogConfig.isEnabled() and
 * MANAGE_EXTERNAL_STORAGE is granted; otherwise falls back to
 * `<app filesDir>/logs/`. Bound: keep last 20 sessions.
 *
 * All public methods are infallible (catch Throwable). When disabled,
 * every call is a fast no-op.
 */
object TraceLogger {
    private const val TAG = "TraceLogger"
    private const val FILE_PREFIX = "trace-"
    private const val FILE_EXT = ".log"
    private const val MAX_FILE_BYTES = 5 * 1024 * 1024 // 5MB
    private const val KEEP_SESSIONS = 20

    private val lock = ReentrantReadWriteLock()
    private val tsFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile private var logFile: File? = null
    @Volatile private var enabled: Boolean = false

    /** Initialize logger. Safe to call multiple times. Never throws. */
    fun init(context: Context) {
        try {
            if (!DevLogConfig.isEnabled()) {
                enabled = false
                logFile = null
                Log.d(TAG, "Dev logs disabled (release build or devLogs flag off)")
                return
            }
            lock.write {
                val dir = DevLogConfig.resolveLogsDir(context) ?: run {
                    enabled = false
                    Log.w(TAG, "No logs dir resolvable; trace logger disabled")
                    return@write
                }
                DevLogConfig.pruneOldFiles(dir, FILE_PREFIX, KEEP_SESSIONS - 1)
                val name = FILE_PREFIX + sessionFormat.format(Date()) + FILE_EXT
                logFile = File(dir, name)
                enabled = true
                writeHeader()
                Log.d(TAG, "TraceLogger initialized: ${logFile?.absolutePath}")
            }
        } catch (t: Throwable) {
            enabled = false
            logFile = null
            Log.w(TAG, "TraceLogger init failed: ${t.message}")
        }
    }

    fun trace(tag: String, message: String) {
        if (!enabled) return
        try {
            val line = "[${tsFormat.format(Date())}] [$tag] $message\n"
            Log.d(tag, message)
            writeToFile(line)
        } catch (t: Throwable) {
            // never propagate
            Log.w(TAG, "trace() suppressed: ${t.message}")
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled) {
            // Still surface to logcat so debugging without file logs works
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            return
        }
        try {
            val sb = StringBuilder()
                .append('[').append(tsFormat.format(Date())).append("] [ERROR][").append(tag).append("] ")
                .append(message)
            if (throwable != null) {
                sb.append('\n').append("Exception: ").append(throwable.javaClass.name)
                    .append(": ").append(throwable.message ?: "").append('\n')
                    .append(throwable.stackTraceToString())
            }
            sb.append('\n')
            Log.e(tag, message, throwable)
            writeToFile(sb.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "error() suppressed: ${t.message}")
        }
    }

    fun lifecycle(component: String, event: String, details: String = "") {
        val msg = if (details.isNotEmpty()) "$event - $details" else event
        trace("Lifecycle:$component", msg)
    }

    /** Reads the current session's trace file. Empty string if disabled/unavailable. */
    fun getTraces(): String {
        if (!enabled) return ""
        return try {
            lock.read { logFile?.takeIf { it.exists() }?.readText() ?: "" }
        } catch (t: Throwable) {
            Log.w(TAG, "getTraces() failed: ${t.message}")
            ""
        }
    }

    /** Truncate the current session file. */
    fun clear() {
        if (!enabled) return
        try {
            lock.write {
                logFile?.writeText("")
                writeHeader()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "clear() suppressed: ${t.message}")
        }
    }

    /** For diagnostics / display: where logs go right now. */
    fun currentLogPath(): String? = logFile?.absolutePath

    private fun writeHeader() {
        val header = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("   HyperWhisper Trace Log")
            appendLine("═══════════════════════════════════════")
            appendLine("Session Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Path: ${logFile?.absolutePath}")
            appendLine("═══════════════════════════════════════")
            appendLine()
        }
        writeToFile(header)
    }

    private fun writeToFile(message: String) {
        lock.write {
            try {
                val f = logFile ?: return@write
                if (f.length() > MAX_FILE_BYTES) truncateInPlace(f)
                FileWriter(f, true).use { it.write(message) }
            } catch (t: Throwable) {
                Log.w(TAG, "writeToFile suppressed: ${t.message}")
            }
        }
    }

    private fun truncateInPlace(file: File) {
        try {
            val keep = file.readLines().let { it.takeLast(it.size / 2) }
            file.writeText(buildString {
                appendLine("═══ Log truncated at ${tsFormat.format(Date())} ═══")
                appendLine()
                keep.forEach { appendLine(it) }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "truncateInPlace suppressed: ${t.message}")
            try { file.writeText("") } catch (_: Throwable) {}
        }
    }
}
