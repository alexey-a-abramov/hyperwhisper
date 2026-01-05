package com.hyperwhisper.network

import com.hyperwhisper.native_whisper.WhisperProgressCallback
import com.hyperwhisper.native_whisper.WhisperSegmentCallback

/**
 * Interface for whisper callback registration
 * Implemented by LocalWhisperStrategy (real) and LocalWhisperStrategyStub (no-op)
 */
interface LocalWhisperCallbacks {
    fun setCallbacks(
        progressCallback: WhisperProgressCallback? = null,
        segmentCallback: WhisperSegmentCallback? = null
    )
    fun clearCallbacks()
}
