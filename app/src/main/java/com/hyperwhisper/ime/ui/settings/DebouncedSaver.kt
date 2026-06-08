package com.hyperwhisper.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces high-frequency save callbacks (per-keystroke text fields) so the
 * repository isn't rewritten — and the field state rebuilt by the resulting
 * settings emission — on every character. A pending save is flushed when the
 * owning composable leaves composition, so no edit is ever lost.
 */
class DebouncedSaver internal constructor(
    private val scope: CoroutineScope,
    private val debounceMs: Long,
) {
    internal var save: () -> Unit = {}
    private var job: Job? = null

    /** Schedule a save after the debounce window, replacing any pending one. */
    fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            job = null
            save()
        }
    }

    /** Drop a pending save (caller is about to persist through another path). */
    fun cancelPending() {
        job?.cancel()
        job = null
    }

    /** Run the pending save immediately, if any. */
    fun flush() {
        if (job != null) {
            cancelPending()
            save()
        }
    }
}

/**
 * Remembers a [DebouncedSaver] bound to the composition. [save] should read
 * the latest field state when invoked (the saver always calls the most recent
 * lambda, so captured `mutableStateOf` vars are read at save time).
 */
@Composable
fun rememberDebouncedSaver(
    debounceMs: Long = 400L,
    save: () -> Unit,
): DebouncedSaver {
    val scope = rememberCoroutineScope()
    val saver = remember { DebouncedSaver(scope, debounceMs) }
    SideEffect { saver.save = save }
    DisposableEffect(saver) {
        onDispose { saver.flush() }
    }
    return saver
}
