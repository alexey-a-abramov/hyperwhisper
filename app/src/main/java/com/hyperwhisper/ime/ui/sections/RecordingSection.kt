package com.hyperwhisper.ui.sections

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * Right: Delete and Enter buttons
 */
@Composable
fun RecordingSection(
    recordingState: RecordingState,
    recordingDuration: Long,
    transcriptionProgress: Float?,
    processingStage: ProcessingStage?,
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
    onToggleTimer: () -> Unit,
    onDelete: () -> Unit,
    onDeleteAll: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Far left: Input field info OR Cancel button
        Box(
            modifier = Modifier.width(80.dp),
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
                        contentPadding = PaddingValues(4.dp)
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
                    // Show input field info when not recording (only in techie mode)
                    if (techieModeEnabled) {
                        InputFieldInfo(
                            editorInfo = editorInfo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Center: Microphone Button + Timer
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
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
                    walkieTalkieMode = walkieTalkieMode,
                    recordingDuration = recordingDuration,
                    transcriptionProgress = transcriptionProgress,
                    processingStage = processingStage,
                    audioFileSize = lastAudioFileSize,
                    audioDurationSeconds = lastAudioDuration,
                    modifier = Modifier
                )

                // Timer display (right of mic) - clickable to toggle
                if (recordingState == RecordingState.RECORDING) {
                    Spacer(Modifier.width(8.dp))
                    RecordingTimer(
                        durationMs = recordingDuration,
                        maxDurationMs = 180000L,
                        isVisible = showTimerText,
                        onToggle = onToggleTimer
                    )
                }
            }
        }

        // Right side: Delete and Enter buttons stacked
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(80.dp)
        ) {
            // Delete button with repeat functionality
            RepeatableDeleteButton(
                onDelete = onDelete,
                onDeleteAll = onDeleteAll,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )

            // Enter button (minimal with just icon)
            Surface(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape,
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
