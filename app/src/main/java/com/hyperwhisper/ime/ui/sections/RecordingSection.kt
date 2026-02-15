package com.hyperwhisper.ui.sections

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ProcessingPhase
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.buttons.MicrophoneButton
import com.hyperwhisper.ui.buttons.RepeatableDeleteButton
import com.hyperwhisper.ui.components.InputFieldInfo
import com.hyperwhisper.ui.indicators.RecordingTimer

/**
 * Recording section - the main interactive area
 * Left: Cancel button (during recording) or InputFieldInfo (techie mode)
 * Center: Microphone button + Timer
 * Right: Enter button
 */
@Composable
fun RecordingSection(
    recordingState: RecordingState,
    recordingDuration: Long,
    transcriptionProgress: Float?,
    processingStage: ProcessingStage?,
    processingPhase: ProcessingPhase,
    lastAudioFileSize: Long,
    lastAudioDuration: Double,
    editorInfo: EditorInfo?,
    techieModeEnabled: Boolean,
    showTimerText: Boolean,
    walkieTalkieMode: Boolean = false,
    onCancelRecording: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelTranscription: () -> Unit,
    onEnableWalkieTalkieMode: () -> Unit = {},
    onDisableWalkieTalkieMode: () -> Unit = {},
    onPressStartRecording: () -> Unit = {},
    onPressReleaseRecording: () -> Unit = {},
    onConfirmRecording: () -> Unit = {},
    onToggleTimer: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Main Row: Cancel/Info (left) + Mic + Timer (center) + Enter (right)
    Row(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Far left: Cancel button during recording
        Box(
            modifier = Modifier.width(70.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            when (recordingState) {
                RecordingState.RECORDING -> {
                    // Show cancel button during recording
                    OutlinedButton(
                        onClick = onCancelRecording,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = strings.cancelDesc,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                strings.cancel.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                else -> {
                    // Empty space when not recording
                }
            }
        }

        // Center: Microphone Button + Timer
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                MicrophoneButton(
                    recordingState = recordingState,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onCancelTranscription = onCancelTranscription,
                    onEnableWalkieTalkieMode = onEnableWalkieTalkieMode,
                    onDisableWalkieTalkieMode = onDisableWalkieTalkieMode,
                    onPressStartRecording = onPressStartRecording,
                    onPressReleaseRecording = onPressReleaseRecording,
                    onConfirmRecording = onConfirmRecording,
                    walkieTalkieMode = walkieTalkieMode,
                    recordingDuration = recordingDuration,
                    transcriptionProgress = transcriptionProgress,
                    processingStage = processingStage,
                    processingPhase = processingPhase,
                    audioFileSize = lastAudioFileSize,
                    audioDurationSeconds = lastAudioDuration,
                    modifier = Modifier
                )

                // Timer display below mic - clickable to toggle
                if (recordingState == RecordingState.RECORDING) {
                    Spacer(Modifier.height(4.dp))
                    RecordingTimer(
                        durationMs = recordingDuration,
                        maxDurationMs = 180000L,
                        isVisible = showTimerText,
                        onToggle = onToggleTimer
                    )
                }
            }
        }

        // Right side: Enter button (square, tall as the section)
        Surface(
            onClick = onEnter,
            modifier = Modifier.width(90.dp).fillMaxHeight().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardReturn,
                    contentDescription = strings.enterDesc,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}
