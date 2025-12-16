package com.hyperwhisper.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe trace logger that writes to a file
 * File is cleared on app restart for fresh diagnostics
 */
object TraceLogger {
    private const val TAG = "TraceLogger"
    private const val LOG_FILE_NAME = "hyperwhisper_trace.log"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB max

    private var logFile: File? = null
    private val lock = ReentrantReadWriteLock()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var isInitialized = false

    /**
     * Initialize the logger - call this in Application.onCreate()
     * Clears the log file from previous session
     */
    fun init(context: Context) {
        lock.write {
            try {
                logFile = File(context.filesDir, LOG_FILE_NAME)

                // Clear the log file on app restart
                logFile?.writeText("")

                isInitialized = true

                // Write header
                writeHeader()

                Log.d(TAG, "TraceLogger initialized at: ${logFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TraceLogger", e)
            }
        }
    }

    private fun writeHeader() {
        val header = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("   HyperWhisper Trace Log")
            appendLine("═══════════════════════════════════════")
            appendLine("Session Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("═══════════════════════════════════════")
            appendLine()
        }
        writeToFile(header)
    }

    /**
     * Log a trace message
     */
    fun trace(tag: String, message: String) {
        if (!isInitialized) return

        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [$tag] $message"

        // Also log to logcat
        Log.d(tag, message)

        // Write to file
        writeToFile(logMessage + "\n")
    }

    /**
     * Log an error with exception
     */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (!isInitialized) return

        val timestamp = dateFormat.format(Date())
        val logMessage = buildString {
            append("[$timestamp] [ERROR][$tag] $message")
            if (throwable != null) {
                append("\n")
                append("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                append("\n")
                append(throwable.stackTraceToString())
            }
        }

        // Also log to logcat
        Log.e(tag, message, throwable)

        // Write to file
        writeToFile(logMessage + "\n")
    }

    /**
     * Log lifecycle event
     */
    fun lifecycle(component: String, event: String, details: String = "") {
        val message = if (details.isNotEmpty()) {
            "$event - $details"
        } else {
            event
        }
        trace("Lifecycle:$component", message)
    }

    /**
     * Get all trace logs as a string
     */
    fun getTraces(): String {
        return lock.read {
            try {
                logFile?.readText() ?: "No traces available"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read trace log", e)
                "Error reading traces: ${e.message}"
            }
        }
    }

    /**
     * Clear the trace log
     */
    fun clear() {
        lock.write {
            try {
                logFile?.writeText("")
                writeHeader()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear trace log", e)
            }
        }
    }

    private fun writeToFile(message: String) {
        lock.write {
            try {
                val file = logFile ?: return@write

                // Check file size and truncate if needed
                if (file.length() > MAX_LOG_SIZE) {
                    truncateLog(file)
                }

                // Append to file
                FileWriter(file, true).use { writer ->
                    writer.write(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to trace log", e)
            }
        }
    }

    private fun truncateLog(file: File) {
        try {
            // Keep last 50% of the log
            val lines = file.readLines()
            val keepLines = lines.takeLast(lines.size / 2)

            file.writeText(buildString {
                appendLine("═══ Log truncated at ${dateFormat.format(Date())} ═══")
                appendLine()
                keepLines.forEach { appendLine(it) }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to truncate log", e)
            // If truncation fails, just clear it
            file.writeText("")
            writeHeader()
        }
    }
}
