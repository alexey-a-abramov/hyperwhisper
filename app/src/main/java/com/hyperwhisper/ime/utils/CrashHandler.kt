package com.hyperwhisper.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val TAG = "CrashHandler"
        @Volatile
        private var crashShown = false

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.d(TAG, "Global crash handler installed")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)

            // Only show crash dialog once per session
            if (!crashShown) {
                crashShown = true

                // Log the crash
                TraceLogger.error("CrashHandler", "Uncaught exception in thread ${thread.name}", throwable)

                // Collect crash information
                val crashInfo = collectCrashInfo(thread, throwable)
                val traces = TraceLogger.getTraces()

                // Launch error activity
                val intent = Intent(context, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("crash_info", crashInfo)
                    putExtra("trace_logs", traces)
                }
                context.startActivity(intent)

                // Give the activity time to start
                Thread.sleep(500)
            } else {
                Log.w(TAG, "Crash already shown in this session, suppressing duplicate crash screen")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling crash", e)
            // Fall back to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            // Exit the app
            exitProcess(1)
        }
    }

    private fun collectCrashInfo(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("   HyperWhisper Crash Report")
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine()

        // Timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        sb.appendLine("Crash Time: ${dateFormat.format(Date())}")
        sb.appendLine()

        // App info
        sb.appendLine("App Information:")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.appendLine("  Package: ${packageInfo.packageName}")
            sb.appendLine("  Version: ${packageInfo.versionName} (Build ${packageInfo.versionCode})")

            // Build timestamp (from package install time as proxy)
            val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(packageInfo.lastUpdateTime))
            sb.appendLine("  Build Date: $buildDate")
        } catch (e: Exception) {
            sb.appendLine("  Package: ${context.packageName}")
            sb.appendLine("  Version: Unknown")
        }
        sb.appendLine()

        // Device info
        sb.appendLine("Device Information:")
        sb.appendLine("  Manufacturer: ${android.os.Build.MANUFACTURER}")
        sb.appendLine("  Model: ${android.os.Build.MODEL}")
        sb.appendLine("  Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("  Architecture: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine()

        // Thread info
        sb.appendLine("Thread Information:")
        sb.appendLine("  Thread: ${thread.name} (ID: ${thread.id})")
        sb.appendLine("  Priority: ${thread.priority}")
        sb.appendLine()

        // Exception details
        sb.appendLine("Exception Information:")
        sb.appendLine("  Type: ${throwable.javaClass.name}")
        sb.appendLine("  Message: ${throwable.message ?: "No message"}")
        sb.appendLine()

        // Stack trace
        sb.appendLine("Stack Trace:")
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        sb.appendLine(stringWriter.toString())

        // Caused by (if present)
        var cause = throwable.cause
        while (cause != null) {
            sb.appendLine()
            sb.appendLine("Caused by:")
            val causeWriter = StringWriter()
            val causePrintWriter = PrintWriter(causeWriter)
            cause.printStackTrace(causePrintWriter)
            sb.appendLine(causeWriter.toString())
            cause = cause.cause
        }

        // Memory info
        sb.appendLine()
        sb.appendLine("Memory Information:")
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        sb.appendLine("  Max Memory: ${maxMemory}MB")
        sb.appendLine("  Total Memory: ${totalMemory}MB")
        sb.appendLine("  Used Memory: ${usedMemory}MB")
        sb.appendLine("  Free Memory: ${freeMemory}MB")

        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════")

        return sb.toString()
    }
}
