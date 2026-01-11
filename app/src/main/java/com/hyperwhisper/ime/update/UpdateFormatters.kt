package com.hyperwhisper.ime.update

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Utility object for formatting update-related data
 */
object UpdateFormatters {

    /**
     * Format file size in bytes to human-readable format
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Format time difference for display
     * Shows positive differences with + prefix, negative with -
     */
    fun formatTimeDifference(diffMs: Long): String {
        val absDiff = abs(diffMs)
        val sign = if (diffMs >= 0) "+" else "-"
        return when {
            absDiff < 1000 -> "${sign}${absDiff}ms"
            absDiff < 60_000 -> "${sign}${absDiff / 1000}s"
            absDiff < 3600_000 -> "${sign}${absDiff / 60_000}m"
            absDiff < 86400_000 -> "${sign}${absDiff / 3600_000}h"
            else -> "${sign}${absDiff / 86400_000}d"
        }
    }

    /**
     * Format Unix timestamp to human-readable date string
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Shorten file path for display
     * Keeps first and last parts, replaces middle with ...
     */
    fun shortenPath(path: String, maxLength: Int = 50): String {
        if (path.length <= maxLength) return path

        val parts = path.split("/")
        if (parts.size <= 3) return path

        val first = parts.take(2).joinToString("/")
        val last = parts.takeLast(1).joinToString("/")

        return "$first/.../$ last"
    }
}
