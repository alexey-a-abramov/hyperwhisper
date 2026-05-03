package com.hyperwhisper.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_FILE_PREFIX = "crash-"
        private const val CRASH_FILE_EXT = ".txt"
        private const val KEEP_CRASHES = 20

        @Volatile private var crashShown = false

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.d(TAG, "Global crash handler installed")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)

            val crashInfo = runCatching { collectCrashInfo(thread, throwable) }
                .getOrElse { "Crash report unavailable: ${it.message}" }
            val traces = runCatching { TraceLogger.getTraces() }.getOrElse { "" }

            // Persist crash file BEFORE launching the activity, in case the
            // activity itself fails to launch (e.g. :crash process issues).
            persistCrashFile(crashInfo, traces)

            if (!crashShown) {
                crashShown = true
                runCatching { TraceLogger.error("CrashHandler", "Uncaught in ${thread.name}", throwable) }

                val intent = Intent(context, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("crash_info", crashInfo)
                    putExtra("trace_logs", traces)
                }
                runCatching { context.startActivity(intent) }
                Thread.sleep(500)
            } else {
                Log.w(TAG, "Crash already shown; suppressing duplicate screen")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling crash", t)
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            exitProcess(1)
        }
    }

    private fun persistCrashFile(crashInfo: String, traces: String) {
        if (!DevLogConfig.isEnabled()) return
        try {
            val dir = DevLogConfig.resolveLogsDir(context) ?: return
            DevLogConfig.pruneOldFiles(dir, CRASH_FILE_PREFIX, KEEP_CRASHES - 1)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "$CRASH_FILE_PREFIX$stamp$CRASH_FILE_EXT")
            val body = buildString {
                append(crashInfo)
                appendLine()
                appendLine("─────── TRACE LOGS ───────")
                append(traces)
            }
            file.writeText(body)
            Log.i(TAG, "Crash persisted: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "persistCrashFile suppressed: ${t.message}")
        }
    }

    private fun collectCrashInfo(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("   HyperWhisper Crash Report")
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        sb.appendLine("Crash Time: ${dateFormat.format(Date())}")
        sb.appendLine()

        sb.appendLine("App Information:")
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.appendLine("  Package: ${pi.packageName}")
            sb.appendLine("  Version: ${pi.versionName} (Build ${PackageInfoCompat.getLongVersionCode(pi)})")
            val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(pi.lastUpdateTime))
            sb.appendLine("  Build Date: $buildDate")
        } catch (t: Throwable) {
            sb.appendLine("  Package: ${context.packageName}")
            sb.appendLine("  Version: Unknown")
        }
        sb.appendLine()

        sb.appendLine("Device Information:")
        sb.appendLine("  Manufacturer: ${android.os.Build.MANUFACTURER}")
        sb.appendLine("  Model: ${android.os.Build.MODEL}")
        sb.appendLine("  Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("  Architecture: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine()

        sb.appendLine("Thread Information:")
        sb.appendLine("  Thread: ${thread.name} (ID: ${thread.id})")
        sb.appendLine("  Priority: ${thread.priority}")
        sb.appendLine()

        sb.appendLine("Exception Information:")
        sb.appendLine("  Type: ${throwable.javaClass.name}")
        sb.appendLine("  Message: ${throwable.message ?: "No message"}")
        sb.appendLine()

        sb.appendLine("Stack Trace:")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        sb.appendLine(sw.toString())

        var cause = throwable.cause
        while (cause != null) {
            sb.appendLine()
            sb.appendLine("Caused by:")
            val cw = StringWriter()
            cause.printStackTrace(PrintWriter(cw))
            sb.appendLine(cw.toString())
            cause = cause.cause
        }

        sb.appendLine()
        sb.appendLine("Memory Information:")
        val rt = Runtime.getRuntime()
        val max = rt.maxMemory() / (1024 * 1024)
        val total = rt.totalMemory() / (1024 * 1024)
        val free = rt.freeMemory() / (1024 * 1024)
        sb.appendLine("  Max Memory: ${max}MB")
        sb.appendLine("  Total Memory: ${total}MB")
        sb.appendLine("  Used Memory: ${total - free}MB")
        sb.appendLine("  Free Memory: ${free}MB")
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════")
        return sb.toString()
    }
}
