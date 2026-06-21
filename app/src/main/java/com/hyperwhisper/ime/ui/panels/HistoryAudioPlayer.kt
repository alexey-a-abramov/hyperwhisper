package com.hyperwhisper.ui.panels

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Snapshot of the single-stream history player for the UI to render. */
data class HistoryPlaybackState(
    val itemId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/**
 * Single-stream audio player for transcription history.
 *
 * Only one recording plays at a time — starting another stops the previous.
 * The per-row control toggles play/pause on the active item. A paused item is
 * auto-released ("decays to stopped") after [PAUSE_DECAY_MS], so a forgotten
 * pause doesn't sit on a MediaPlayer indefinitely; the next tap then restarts
 * it from the top.
 *
 * Must be driven from a main-thread [scope] (MediaPlayer is thread-affine):
 * pass `rememberCoroutineScope()`.
 */
class HistoryAudioPlayer(private val scope: CoroutineScope) {
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var decayJob: Job? = null

    private val _state = MutableStateFlow(HistoryPlaybackState())
    val state: StateFlow<HistoryPlaybackState> = _state.asStateFlow()

    /**
     * Play [itemId] from [path]. If it's already the active item, toggle
     * play/pause instead of restarting.
     */
    fun toggle(itemId: String, path: String) {
        val cur = _state.value
        if (cur.itemId == itemId && player != null) {
            if (cur.isPlaying) pause() else resume()
        } else {
            start(itemId, path)
        }
    }

    private fun start(itemId: String, path: String) {
        stopInternal()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(path)
            mp.setOnPreparedListener {
                it.start()
                _state.value = HistoryPlaybackState(
                    itemId = itemId,
                    isPlaying = true,
                    positionMs = 0,
                    durationMs = it.duration.coerceAtLeast(0),
                )
                startTicker()
            }
            mp.setOnCompletionListener { stopInternal() }
            mp.setOnErrorListener { _, _, _ -> stopInternal(); true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            mp.release()
            stopInternal()
        }
    }

    private fun pause() {
        val mp = player ?: return
        runCatching { if (mp.isPlaying) mp.pause() }
        ticker?.cancel()
        _state.value = _state.value.copy(isPlaying = false, positionMs = safePos(mp))
        startDecay()
    }

    private fun resume() {
        val mp = player ?: return
        decayJob?.cancel()
        if (runCatching { mp.start() }.isFailure) return
        _state.value = _state.value.copy(isPlaying = true)
        startTicker()
    }

    /** Seek the active item to [fraction] (0..1) of its duration. */
    fun seekTo(fraction: Float) {
        val mp = player ?: return
        val dur = _state.value.durationMs
        if (dur <= 0) return
        val target = (fraction.coerceIn(0f, 1f) * dur).toInt()
        runCatching { mp.seekTo(target) }
        _state.value = _state.value.copy(positionMs = target)
    }

    fun stop() = stopInternal()

    fun release() = stopInternal()

    private fun stopInternal() {
        ticker?.cancel(); ticker = null
        decayJob?.cancel(); decayJob = null
        player?.let { mp ->
            runCatching { mp.stop() }
            mp.release()
        }
        player = null
        _state.value = HistoryPlaybackState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val mp = player ?: break
                _state.value = _state.value.copy(positionMs = safePos(mp))
                delay(200)
            }
        }
    }

    private fun startDecay() {
        decayJob?.cancel()
        decayJob = scope.launch {
            delay(PAUSE_DECAY_MS)
            stopInternal()
        }
    }

    private fun safePos(mp: MediaPlayer): Int = runCatching { mp.currentPosition }.getOrDefault(0)

    companion object {
        const val PAUSE_DECAY_MS = 60_000L
    }
}
