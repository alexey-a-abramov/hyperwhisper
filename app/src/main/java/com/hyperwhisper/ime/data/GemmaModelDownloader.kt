package com.hyperwhisper.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirror of [WhisperDownloadState] for Gemma. The two state machines are
 * intentionally separate: a single Whisper download driving the UI shouldn't
 * need to know about Gemma sealed-class variants and vice versa, and merging
 * them would couple two independent feature areas.
 */
sealed class GemmaDownloadState {
    object Idle : GemmaDownloadState()
    object Queued : GemmaDownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSec: Long,
        val etaSeconds: Int
    ) : GemmaDownloadState()
    /** Transient failure mid-download. The downloader auto-retries with
     * exponential backoff before falling through to [Failed]. */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
        val lastError: String,
        val resumableBytes: Long,
        val backoffSeconds: Int
    ) : GemmaDownloadState()
    data class Completed(val path: String) : GemmaDownloadState()
    /** Network or other transient error. [resumableBytes] is the size of the
     * .part on disk — non-zero means the next start can resume via Range. */
    data class Failed(val message: String, val resumableBytes: Long = 0L) : GemmaDownloadState()
    /** User cancelled or the connection dropped; .part is kept on disk and
     * [resumableBytes] reflects how much can be resumed. */
    data class Paused(val resumableBytes: Long) : GemmaDownloadState()
    object Cancelled : GemmaDownloadState()
}

/**
 * Drives Gemma-model downloads with the same retry / Range-resume contract as
 * [WhisperModelDownloader]. The implementation is a near-mechanical copy: the
 * two state machines are kept separable on purpose (see [GemmaDownloadState]
 * kdoc), but the I/O pump is identical.
 *
 * Storage layout: `/sdcard/LLM/Gemma/<file>` when MANAGE_EXTERNAL_STORAGE is
 * granted, else `<filesDir>/gemma/<file>`. Mirrors the Whisper directory
 * contract so other tooling (LocalModelRepository scan, Termux inspection)
 * finds them in the expected place.
 */
@Singleton
class GemmaModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GemmaDownloader"
        private const val EMIT_INTERVAL_MS = 400L
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1500L
        private const val MAX_BACKOFF_MS = 20_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, GemmaDownloadState>>(initialStates())
    val states: StateFlow<Map<String, GemmaDownloadState>> = _states.asStateFlow()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // 90s read timeout: HF CDN occasionally stalls mid-stream on
            // large LLM files; lower values produced spurious aborts on
            // multi-GB transfers in testing.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun initialStates(): Map<String, GemmaDownloadState> =
        GemmaModelCatalog.ALL.associate { entry ->
            val full = targetFile(entry)
            val partial = partFile(entry)
            entry.id to when {
                full.exists() -> GemmaDownloadState.Completed(full.absolutePath)
                partial.exists() && partial.length() > 0 ->
                    GemmaDownloadState.Paused(partial.length())
                else -> GemmaDownloadState.Idle
            }
        }

    fun isDownloaded(entry: GemmaModelEntry): Boolean = targetFile(entry).exists()

    fun localPath(entry: GemmaModelEntry): String? {
        val f = targetFile(entry)
        return if (f.exists()) f.absolutePath else null
    }

    fun start(entry: GemmaModelEntry) {
        synchronized(jobs) {
            if (jobs[entry.id]?.isActive == true) return
            if (isDownloaded(entry)) {
                update(entry.id, GemmaDownloadState.Completed(targetFile(entry).absolutePath))
                return
            }
            update(entry.id, GemmaDownloadState.Queued)
            jobs[entry.id] = scope.launch { runDownload(entry) }
        }
        // Promote process to foreground so the download survives the IME
        // closing or the app being backgrounded. Service stops itself when
        // the queue empties (across both Whisper + Gemma).
        try {
            ModelDownloadService.start(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start ModelDownloadService — download will run unprotected", t)
        }
    }

    /**
     * Stop the in-flight transfer and surface state. The .part file is left on
     * disk so a follow-up [start] can resume via HTTP Range. Use [delete] to
     * remove a half-downloaded file explicitly.
     */
    fun cancel(modelId: String) {
        synchronized(jobs) {
            jobs[modelId]?.cancel()
        }
        val resumable = GemmaModelCatalog.byId(modelId)?.let { partFile(it).length() } ?: 0L
        update(
            modelId,
            if (resumable > 0L) GemmaDownloadState.Paused(resumable)
            else GemmaDownloadState.Cancelled
        )
    }

    fun delete(entry: GemmaModelEntry): Boolean {
        synchronized(jobs) { jobs[entry.id]?.cancel() }
        partFile(entry).takeIf { it.exists() }?.delete()
        val f = targetFile(entry)
        val ok = if (f.exists()) f.delete() else true
        update(entry.id, GemmaDownloadState.Idle)
        return ok
    }

    fun reset(modelId: String) {
        update(modelId, GemmaDownloadState.Idle)
    }

    /**
     * Drive a Gemma-model download with HTTP Range resume + automatic retry.
     * See [WhisperModelDownloader.runDownload] for the state-machine rationale
     * — this is a structural mirror.
     */
    private suspend fun runDownload(entry: GemmaModelEntry) {
        val target = targetFile(entry)
        val partial = partFile(entry)
        target.parentFile?.mkdirs()

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                when (val outcome = attemptDownload(entry, target, partial)) {
                    is AttemptResult.Completed -> {
                        update(entry.id, GemmaDownloadState.Completed(outcome.path))
                        Log.i(TAG, "Downloaded ${entry.id} → ${outcome.path} (${target.length()} B)")
                        return
                    }
                    AttemptResult.Cancelled -> {
                        update(entry.id, GemmaDownloadState.Paused(partial.length()))
                        return
                    }
                    is AttemptResult.HardFail -> {
                        update(
                            entry.id,
                            GemmaDownloadState.Failed(outcome.message, partial.length())
                        )
                        return
                    }
                    is AttemptResult.Transient -> {
                        lastError = outcome.cause
                        Log.w(TAG, "Transient failure on ${entry.id}, attempt $attempt/$MAX_ATTEMPTS", outcome.cause)
                        if (attempt < MAX_ATTEMPTS) {
                            val backoffMs = computeBackoffMs(attempt)
                            update(
                                entry.id,
                                GemmaDownloadState.Retrying(
                                    attempt = attempt,
                                    maxAttempts = MAX_ATTEMPTS,
                                    lastError = outcome.cause.message ?: outcome.cause.javaClass.simpleName,
                                    resumableBytes = partial.length(),
                                    backoffSeconds = (backoffMs / 1000).toInt().coerceAtLeast(1)
                                )
                            )
                            try {
                                delay(backoffMs)
                            } catch (e: CancellationException) {
                                update(entry.id, GemmaDownloadState.Paused(partial.length()))
                                throw e
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                update(entry.id, GemmaDownloadState.Paused(partial.length()))
                throw e
            }
        }

        // Exhausted all retries.
        update(
            entry.id,
            GemmaDownloadState.Failed(
                message = "Failed after $attempt attempts: ${lastError?.message ?: "network error"}",
                resumableBytes = partial.length()
            )
        )
    }

    private fun computeBackoffMs(attempt: Int): Long {
        // Exponential: 1.5s, 3s, 6s, 12s, capped at MAX_BACKOFF_MS.
        val expBackoff = INITIAL_BACKOFF_MS shl (attempt - 1).coerceIn(0, 6)
        return expBackoff.coerceAtMost(MAX_BACKOFF_MS)
    }

    private sealed class AttemptResult {
        data class Completed(val path: String) : AttemptResult()
        object Cancelled : AttemptResult()
        /** A 4xx (other than 416) or other unrecoverable condition. Don't retry. */
        data class HardFail(val message: String) : AttemptResult()
        /** A network glitch / 5xx / dropped connection. Retry with backoff. */
        data class Transient(val cause: Throwable) : AttemptResult()
    }

    /**
     * One download attempt. Catches IOExceptions internally so the caller
     * can decide whether to retry. The .part stays on disk between attempts.
     */
    private suspend fun attemptDownload(
        entry: GemmaModelEntry,
        target: File,
        partial: File
    ): AttemptResult {
        val resumeFrom = if (partial.exists()) partial.length() else 0L

        val builder = Request.Builder().url(entry.downloadUrl)
        if (resumeFrom > 0L) builder.addHeader("Range", "bytes=$resumeFrom-")

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: java.io.IOException) {
            return AttemptResult.Transient(e)
        }

        try {
            val code = response.code
            // 416: server says our resume offset is past EOF — i.e. the .part
            // is already the full file. Promote to completed.
            if (code == 416 && partial.exists() && partial.length() > 0) {
                response.close()
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                return AttemptResult.Completed(target.absolutePath)
            }

            val acceptedRange = code == 206
            val freshStart = code == 200
            if (!acceptedRange && !freshStart) {
                response.close()
                // 5xx → transient and worth a retry. 4xx → hard fail (auth,
                // missing file, gated-license rejection — won't fix itself).
                return if (code in 500..599) {
                    AttemptResult.Transient(java.io.IOException("HTTP $code"))
                } else {
                    AttemptResult.HardFail("HTTP $code")
                }
            }

            val body = response.body
                ?: run {
                    response.close()
                    return AttemptResult.Transient(java.io.IOException("Empty response body"))
                }

            val effectiveResume = if (acceptedRange) resumeFrom else 0L
            val advertisedRemaining = body.contentLength()
            val total: Long = if (acceptedRange) {
                val cr = response.header("Content-Range").orEmpty()
                val full = cr.substringAfterLast('/', "").toLongOrNull() ?: -1L
                when {
                    full > 0 -> full
                    advertisedRemaining > 0 -> resumeFrom + advertisedRemaining
                    else -> entry.sizeBytes
                }
            } else {
                if (advertisedRemaining > 0) advertisedRemaining else entry.sizeBytes
            }

            if (freshStart && partial.exists()) partial.delete()

            update(entry.id, GemmaDownloadState.Downloading(effectiveResume, total, 0L, -1))

            try {
                streamBody(entry, body, partial, effectiveResume, total)
            } catch (e: java.io.IOException) {
                return AttemptResult.Transient(e)
            }

            if (!currentCoroutineContext().isActive) {
                return AttemptResult.Cancelled
            }

            // Sanity check: did we actually get the full file?
            val onDisk = partial.length()
            if (total > 0 && onDisk < total) {
                return AttemptResult.Transient(
                    java.io.IOException("Short read: $onDisk / $total bytes")
                )
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return AttemptResult.Completed(target.absolutePath)
        } finally {
            runCatching { response.close() }
        }
    }

    /**
     * Read [body] into [partial] starting at [resumeOffset] (already-written
     * prefix). Emits Downloading state every [EMIT_INTERVAL_MS]. Throws
     * IOException on read failure so the caller can retry.
     */
    @kotlin.jvm.Throws(java.io.IOException::class)
    private suspend fun streamBody(
        entry: GemmaModelEntry,
        body: okhttp3.ResponseBody,
        partial: File,
        resumeOffset: Long,
        total: Long
    ) {
        val source = body.byteStream()
        val sink = FileOutputStream(partial, /*append=*/ resumeOffset > 0L)
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = resumeOffset
        var lastEmitMs = System.currentTimeMillis()
        var lastEmitBytes = downloaded

        sink.use { out ->
            source.use { input ->
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastEmitMs
                    if (elapsed >= EMIT_INTERVAL_MS) {
                        val deltaBytes = downloaded - lastEmitBytes
                        val bps = if (elapsed > 0) deltaBytes * 1000 / elapsed else 0
                        val eta = if (bps > 0 && total > downloaded)
                            ((total - downloaded) / bps).toInt() else -1
                        update(
                            entry.id,
                            GemmaDownloadState.Downloading(downloaded, total, bps, eta)
                        )
                        lastEmitMs = now
                        lastEmitBytes = downloaded
                    }
                }
            }
        }
    }

    private fun update(id: String, state: GemmaDownloadState) {
        _states.value = _states.value.toMutableMap().apply { put(id, state) }
    }

    fun gemmaDir(): File {
        val canExternal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else true
        val base = if (canExternal) {
            File(Environment.getExternalStorageDirectory(), "LLM/Gemma")
        } else {
            File(context.filesDir, "gemma")
        }
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun targetFile(entry: GemmaModelEntry): File = File(gemmaDir(), entry.fileName)
    fun partFile(entry: GemmaModelEntry): File =
        File(gemmaDir(), "${entry.fileName}.part")
}
