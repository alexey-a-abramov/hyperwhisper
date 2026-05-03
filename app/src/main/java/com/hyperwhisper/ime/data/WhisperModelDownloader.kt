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

sealed class WhisperDownloadState {
    object Idle : WhisperDownloadState()
    object Queued : WhisperDownloadState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSec: Long,
        val etaSeconds: Int
    ) : WhisperDownloadState()
    /** Transient failure mid-download. The downloader auto-retries with
     * exponential backoff before falling through to [Failed]. */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
        val lastError: String,
        val resumableBytes: Long,
        val backoffSeconds: Int
    ) : WhisperDownloadState()
    data class Completed(val path: String) : WhisperDownloadState()
    /** Network or other transient error. [resumableBytes] is the size of the
     * .part on disk — non-zero means the next start can resume via Range. */
    data class Failed(val message: String, val resumableBytes: Long = 0L) : WhisperDownloadState()
    /** User cancelled or the connection dropped; .part is kept on disk and
     * [resumableBytes] reflects how much can be resumed. */
    data class Paused(val resumableBytes: Long) : WhisperDownloadState()
    object Cancelled : WhisperDownloadState()
}

@Singleton
class WhisperModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WhisperDownloader"
        private const val EMIT_INTERVAL_MS = 400L
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1500L
        private const val MAX_BACKOFF_MS = 20_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, WhisperDownloadState>>(initialStates())
    val states: StateFlow<Map<String, WhisperDownloadState>> = _states.asStateFlow()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Read timeout is per-socket-read, not whole-request. 90s
            // tolerates HF CDN's occasional mid-stream stalls before we
            // bail and retry; lower values produced spurious aborts.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun initialStates(): Map<String, WhisperDownloadState> =
        WhisperModelCatalog.ALL.associate { entry ->
            val full = targetFile(entry)
            val partial = partFile(entry)
            entry.id to when {
                full.exists() -> WhisperDownloadState.Completed(full.absolutePath)
                partial.exists() && partial.length() > 0 ->
                    WhisperDownloadState.Paused(partial.length())
                else -> WhisperDownloadState.Idle
            }
        }

    fun isDownloaded(entry: WhisperModelEntry): Boolean = targetFile(entry).exists()

    fun localPath(entry: WhisperModelEntry): String? {
        val f = targetFile(entry)
        return if (f.exists()) f.absolutePath else null
    }

    fun start(entry: WhisperModelEntry) {
        synchronized(jobs) {
            if (jobs[entry.id]?.isActive == true) return
            if (isDownloaded(entry)) {
                update(entry.id, WhisperDownloadState.Completed(targetFile(entry).absolutePath))
                return
            }
            update(entry.id, WhisperDownloadState.Queued)
            jobs[entry.id] = scope.launch { runDownload(entry) }
        }
        // Promote process to foreground so the download survives the IME
        // closing or the app being backgrounded. Service stops itself when
        // the queue empties.
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
        val resumable = WhisperModelCatalog.byId(modelId)?.let { partFile(it).length() } ?: 0L
        update(
            modelId,
            if (resumable > 0L) WhisperDownloadState.Paused(resumable)
            else WhisperDownloadState.Cancelled
        )
    }

    fun delete(entry: WhisperModelEntry): Boolean {
        synchronized(jobs) { jobs[entry.id]?.cancel() }
        partFile(entry).takeIf { it.exists() }?.delete()
        val f = targetFile(entry)
        val ok = if (f.exists()) f.delete() else true
        update(entry.id, WhisperDownloadState.Idle)
        return ok
    }

    fun reset(modelId: String) {
        update(modelId, WhisperDownloadState.Idle)
    }

    /**
     * Drive a Whisper-model download with HTTP Range resume + automatic retry.
     *
     * Each attempt either:
     *   - completes the file → return Completed, finish
     *   - is cancelled → return Cancelled, finish
     *   - hits a transient error → swallow, sleep with backoff, retry
     *   - hits a hard error (4xx other than 416) → fail fast
     *
     * .part is preserved across attempts so each retry resumes via Range.
     * 416 (Range Not Satisfiable) is treated as "already complete" — happens
     * when the .part is fully downloaded but we never got to rename it.
     */
    private suspend fun runDownload(entry: WhisperModelEntry) {
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
                        update(entry.id, WhisperDownloadState.Completed(outcome.path))
                        Log.i(TAG, "Downloaded ${entry.id} → ${outcome.path} (${target.length()} B)")
                        return
                    }
                    AttemptResult.Cancelled -> {
                        update(entry.id, WhisperDownloadState.Paused(partial.length()))
                        return
                    }
                    is AttemptResult.HardFail -> {
                        update(
                            entry.id,
                            WhisperDownloadState.Failed(outcome.message, partial.length())
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
                                WhisperDownloadState.Retrying(
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
                                update(entry.id, WhisperDownloadState.Paused(partial.length()))
                                throw e
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                update(entry.id, WhisperDownloadState.Paused(partial.length()))
                throw e
            }
        }

        // Exhausted all retries.
        update(
            entry.id,
            WhisperDownloadState.Failed(
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
        entry: WhisperModelEntry,
        target: java.io.File,
        partial: java.io.File
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
                // missing file, etc. — won't fix itself).
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

            update(entry.id, WhisperDownloadState.Downloading(effectiveResume, total, 0L, -1))

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
        entry: WhisperModelEntry,
        body: okhttp3.ResponseBody,
        partial: java.io.File,
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
                            WhisperDownloadState.Downloading(downloaded, total, bps, eta)
                        )
                        lastEmitMs = now
                        lastEmitBytes = downloaded
                    }
                }
            }
        }
    }

    private fun update(id: String, state: WhisperDownloadState) {
        _states.value = _states.value.toMutableMap().apply { put(id, state) }
    }

    fun whisperDir(): File {
        val canExternal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else true
        val base = if (canExternal) {
            File(Environment.getExternalStorageDirectory(), "LLM/Whisper")
        } else {
            File(context.filesDir, "whisper")
        }
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun targetFile(entry: WhisperModelEntry): File = File(whisperDir(), entry.fileName)
    fun partFile(entry: WhisperModelEntry): File =
        File(whisperDir(), "${entry.fileName}.part")
}
