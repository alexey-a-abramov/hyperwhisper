package com.hyperwhisper.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hyperwhisper.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that holds the app process alive while Whisper-model
 * downloads are in flight. Without this, dismissing the IME (or backgrounding
 * the app) is grounds for the system to reclaim our process mid-download —
 * a 1.5GB Whisper-medium download isn't going to survive that.
 *
 * The service is a thin life-support shim: it doesn't drive the download
 * itself (that's still [WhisperModelDownloader] with its retry/resume/Range
 * logic), it just keeps our notification visible so Android leaves us alone.
 *
 * Lifecycle:
 *  - [WhisperModelDownloader.start] sends an Intent that ContextCompat
 *    starts as a foreground service.
 *  - We observe [WhisperModelDownloader.states]; when no entry is active
 *    (Downloading, Queued, or Retrying), we call [stopSelf].
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var downloader: WhisperModelDownloader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        // Promote to foreground immediately — Android requires this within
        // ~5s of startForegroundService() or it kills us with ANR-like errors.
        startInForeground(initialNotification())

        observer = scope.launch {
            downloader.states.collectLatest { states ->
                val active = states.filterValues { it.isActive() }
                if (active.isEmpty()) {
                    Log.i(TAG, "No active downloads — stopping foreground service")
                    stopSelf()
                } else {
                    NotificationManagerCompat.from(this@ModelDownloadService)
                        .notify(NOTIF_ID, buildNotification(active))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // sticky-not-redelivered: if killed mid-download, .part files survive
        // and the user can manually Resume; we don't auto-restart and burn
        // their data.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun initialNotification(): Notification = baseBuilder()
        .setContentTitle("Preparing download")
        .setProgress(0, 0, /*indeterminate=*/ true)
        .build()

    private fun buildNotification(
        active: Map<String, WhisperDownloadState>
    ): Notification {
        val n = active.size
        val (title, content, progress) = buildText(active)
        val builder = baseBuilder()
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))

        if (progress != null) {
            val (cur, total, indeterminate) = progress
            builder.setProgress(total, cur, indeterminate)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (n > 1) builder.setSubText("$n downloads")
        return builder.build()
    }

    private fun buildText(
        active: Map<String, WhisperDownloadState>
    ): Triple<String, String, Triple<Int, Int, Boolean>?> {
        // If a single active download, show its progress in the bar; else
        // show count + indeterminate bar.
        if (active.size == 1) {
            val (id, state) = active.entries.first()
            val displayName = WhisperModelCatalog.byId(id)?.displayName ?: id
            return when (state) {
                is WhisperDownloadState.Downloading -> {
                    val pct = if (state.totalBytes > 0)
                        ((state.downloadedBytes * 100) / state.totalBytes).toInt() else 0
                    val rate = if (state.bytesPerSec > 0)
                        " · ${formatBps(state.bytesPerSec)}" else ""
                    val eta = if (state.etaSeconds > 0) " · ETA ${state.etaSeconds}s" else ""
                    Triple(
                        "Downloading $displayName",
                        "$pct%$rate$eta",
                        Triple(pct, 100, false)
                    )
                }
                is WhisperDownloadState.Queued -> Triple(
                    "Queued: $displayName", "Waiting to start…", Triple(0, 0, true)
                )
                is WhisperDownloadState.Retrying -> Triple(
                    "Retrying $displayName",
                    "Attempt ${state.attempt}/${state.maxAttempts} · ${state.lastError}",
                    Triple(0, 0, true)
                )
                else -> Triple(displayName, "Working…", Triple(0, 0, true))
            }
        }
        return Triple(
            "${active.size} model downloads",
            "Tap to view progress",
            null
        )
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (openIntent != null) {
            PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply { if (pi != null) setContentIntent(pi) }
    }

    private fun formatBps(bps: Long): String {
        if (bps <= 0) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s")
        var v = bps.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) { v /= 1024.0; i++ }
        return if (i == 0) "${v.toInt()} ${units[i]}" else "%.1f %s".format(v, units[i])
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val CHANNEL_ID = "model_downloads"
        private const val CHANNEL_NAME = "Model downloads"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            ensureChannel(context)
            val intent = Intent(context, ModelDownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Whisper model download progress"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }

        private fun WhisperDownloadState.isActive(): Boolean = when (this) {
            is WhisperDownloadState.Downloading,
            is WhisperDownloadState.Queued,
            is WhisperDownloadState.Retrying -> true
            else -> false
        }
    }
}
