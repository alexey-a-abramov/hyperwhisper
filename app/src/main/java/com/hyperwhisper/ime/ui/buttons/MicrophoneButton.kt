package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.ui.indicators.ProcessingIndicator

@Composable
fun MicrophoneButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelTranscription: () -> Unit = {},
    onEnableWalkieTalkieMode: () -> Unit = {},
    onDisableWalkieTalkieMode: () -> Unit = {},
    onPressStartRecording: () -> Unit = {},
    onPressReleaseRecording: () -> Unit = {},
    walkieTalkieMode: Boolean = false,
    recordingDuration: Long = 0L,
    transcriptionProgress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (recordingState) {
            RecordingState.IDLE -> {
                IdleMicButton(
                    onClick = onStartRecording,
                    onLongPress = onEnableWalkieTalkieMode,
                    onDoubleTap = if (walkieTalkieMode) onDisableWalkieTalkieMode else ({}),
                    onPressStart = onPressStartRecording,
                    onPressRelease = onPressReleaseRecording,
                    walkieTalkieMode = walkieTalkieMode
                )
            }
            RecordingState.RECORDING -> {
                RecordingMicButton(
                    onClick = onStopRecording,
                    onDoubleTap = if (walkieTalkieMode) onDisableWalkieTalkieMode else ({}),
                    recordingDuration = recordingDuration,
                    walkieTalkieMode = walkieTalkieMode
                )
            }
            RecordingState.PROCESSING -> {
                ProcessingIndicator(
                    progress = transcriptionProgress,
                    processingStage = processingStage,
                    audioFileSize = audioFileSize,
                    audioDurationSeconds = audioDurationSeconds,
                    onCancel = onCancelTranscription
                )
            }
            RecordingState.ERROR -> {
                IdleMicButton(
                    onClick = onStartRecording,
                    onLongPress = onEnableWalkieTalkieMode,
                    onDoubleTap = if (walkieTalkieMode) onDisableWalkieTalkieMode else ({}),
                    onPressStart = onPressStartRecording,
                    onPressRelease = onPressReleaseRecording,
                    walkieTalkieMode = walkieTalkieMode
                )
            }
        }
    }
}
