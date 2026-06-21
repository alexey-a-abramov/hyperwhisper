package com.hyperwhisper.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Press-and-hold auto-repeat with hardware-backspace feel.
 *
 * Tap fires [onTrigger] exactly once. Hold fires it immediately, waits
 * [initialDelayMs], then repeats — *accelerating*: the gap starts at
 * [startIntervalMs] and shrinks by [accelerationFactor] each tick down to a
 * [minIntervalMs] floor, so a long hold rips through text the way a real
 * backspace key does. If [onLongHold] is supplied, holding past [longHoldMs]
 * fires it once (e.g. "delete everything") and then stops repeating.
 *
 * Implementation notes:
 *  - Uses [awaitEachGesture] / [awaitFirstDown] rather than `detectTapGestures`.
 *    The latter pays a tap-vs-other disambiguation cost that makes sustained
 *    holds flaky inside an IME — down/up tracking here is direct and reliable.
 *  - Callbacks are read through [rememberUpdatedState] so the long-lived gesture
 *    coroutine never invokes a stale lambda captured at first composition.
 *  - The repeat loop runs on a [rememberCoroutineScope] (Main-dispatched) so
 *    `InputConnection` deletes stay on the UI thread.
 *
 * Do not chain alongside Surface(onClick=…) / IconButton(onClick=…) on the
 * same element: those install their own gesture detector and will either
 * double-fire or swallow the press before this modifier sees it.
 */
fun Modifier.repeatOnHold(
    initialDelayMs: Long = 400L,
    startIntervalMs: Long = 220L,
    minIntervalMs: Long = 28L,
    accelerationFactor: Float = 0.82f,
    longHoldMs: Long = 5000L,
    onLongHold: (() -> Unit)? = null,
    onTrigger: () -> Unit,
): Modifier = composed {
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    val currentOnLongHold by rememberUpdatedState(onLongHold)
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val job: Job = scope.launch {
                currentOnTrigger() // immediate first fire — also the tap case
                delay(initialDelayMs)
                var interval = startIntervalMs
                var heldMs = initialDelayMs
                while (isActive) {
                    if (currentOnLongHold != null && heldMs >= longHoldMs) {
                        currentOnLongHold?.invoke()
                        break
                    }
                    currentOnTrigger()
                    delay(interval)
                    heldMs += interval
                    interval = (interval * accelerationFactor).toLong()
                        .coerceAtLeast(minIntervalMs)
                }
            }
            // Suspends until the finger lifts or the gesture is cancelled; a
            // quick tap lands here during the initial delay, so the loop is
            // cancelled before it ever repeats → exactly one fire.
            waitForUpOrCancellation()
            job.cancel()
        }
    }
}
