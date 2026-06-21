package com.hyperwhisper.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Naming + location convention for persisted recording audio.
 *
 * Files live in `<filesDir>/audio_history` and are named
 *   `rec_yyyyMMdd-HHmmss-SSS.wav`
 * so the name is sortable (lexicographic order == chronological), human
 * readable, and round-trips back to the recording timestamp. That round-trip
 * is what lets history reconcile disk files against DB rows by name and
 * recover the recording time for orphaned files — audio that was saved but,
 * because of a crash, walkie-talkie mode, or an error before the row was
 * written, never got linked into history.
 *
 * Legacy files used `audio_<epochMillis>_<uuid>.wav`; [timestampFor] still
 * parses those so they can be migrated to the new convention in place.
 */
object AudioHistoryFiles {
    const val DIR_NAME = "audio_history"
    private const val PREFIX = "rec_"
    private const val EXT = ".wav"
    private const val STAMP_PATTERN = "yyyyMMdd-HHmmss-SSS"
    private const val LEGACY_PREFIX = "audio_"

    // SimpleDateFormat is not thread-safe; build a fresh one per call. Local
    // timezone on both format and parse so the name matches wall-clock time
    // and round-trips consistently on the same device.
    private fun stampFormat() = SimpleDateFormat(STAMP_PATTERN, Locale.US)

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Canonical file name for a recording made at [timestampMs]. */
    fun nameFor(timestampMs: Long): String =
        PREFIX + stampFormat().format(Date(timestampMs)) + EXT

    /** True when [name] already follows the current convention. */
    fun matchesConvention(name: String): Boolean = parseStamp(name) != null

    /**
     * Best-effort recording timestamp for [file]:
     * 1. the embedded stamp (new convention),
     * 2. the legacy `audio_<epochMillis>_<uuid>.wav` epoch,
     * 3. the file's lastModified() as a final fallback.
     */
    fun timestampFor(file: File): Long {
        parseStamp(file.name)?.let { return it }
        parseLegacyMillis(file.name)?.let { return it }
        return file.lastModified()
    }

    private fun parseStamp(name: String): Long? {
        if (!name.startsWith(PREFIX) || !name.endsWith(EXT)) return null
        val core = name.removePrefix(PREFIX).removeSuffix(EXT)
        return runCatching { stampFormat().parse(core)?.time }.getOrNull()
    }

    private fun parseLegacyMillis(name: String): Long? {
        if (!name.startsWith(LEGACY_PREFIX)) return null
        return name.removePrefix(LEGACY_PREFIX).substringBefore('_').toLongOrNull()
    }
}
