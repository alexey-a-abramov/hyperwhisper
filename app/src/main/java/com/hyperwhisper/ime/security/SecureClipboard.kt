package com.hyperwhisper.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Clipboard helper for sensitive data (API keys / secrets export JSON).
 *
 * Two precautions over a plain `setPrimaryClip`:
 *  1. **Sensitive flag**: on API 33+ the system masks the clipboard preview
 *     UI so the value isn't shown in toasts / pasteboard chips.
 *  2. **Auto-clear**: schedules a wipe after [autoClearMs] but only if the clip
 *     still belongs to us — checked via the clip label so we never overwrite a
 *     subsequent unrelated copy.
 */
object SecureClipboard {

    private const val DEFAULT_AUTO_CLEAR_MS = 30_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun copySensitive(
        context: Context,
        label: String,
        value: String,
        autoClearMs: Long = DEFAULT_AUTO_CLEAR_MS,
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = (clip.description.extras ?: PersistableBundle()).apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clip.description.extras = extras
        }
        cm.setPrimaryClip(clip)

        if (autoClearMs > 0) {
            mainHandler.postDelayed({ clearIfStillOurs(cm, label) }, autoClearMs)
        }
    }

    private fun clearIfStillOurs(cm: ClipboardManager, label: String) {
        try {
            val current = cm.primaryClip ?: return
            if (current.description?.label == label) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cm.clearPrimaryClip()
                } else {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } catch (_: Throwable) {
            // Clipboard ops can throw on locked devices / unusual OEMs; swallow.
        }
    }
}
