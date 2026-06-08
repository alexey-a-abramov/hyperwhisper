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

/**
 * Recording section - the main interactive area.
 * Left: stacked Esc/Tab (when [onEsc]/[onTab] supplied) or empty. During
 *       recording the Cancel button overlays this slot.
 * Center: Microphone button + Timer.
 * Right: Backspace — sits between the Output language chip above and the
 * Enter button below, so the right column reads Out → Backspace → Enter
 * top to bottom. Backspace is repeat-on-hold for the same long-press =
 * delete-fast behaviour as every other layout.
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
    onToggleTimer: () -> Unit,
    onDelete: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    /**
     * When false, the right-column backspace is hidden — used by the new
     * Dictation layout where backspace lives in the top-right header column
     * instead of next to the mic. Keeps the on-by-default behaviour for any
     * other caller untouched.
     */
    showBackspace: Boolean = true,
    /**
     * Esc/Tab callbacks. When both are supplied, stacked Esc-above-Tab keys
     * are rendered in the left slot — including during recording, so the
     * keys never disappear mid-dictation. The Cancel button instead overlays
     * the left edge of the mic area during recording.
     */
    onEsc: (() -> Unit)? = null,
    onTab: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val showStackedEscTab = onEsc != null && onTab != null
    val isRecording = recordingState == RecordingState.RECORDING

    // Main Row: Esc/Tab (left, always) + Mic + cancel overlay (center) + Backspace (right, optional)
    Row(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Far left: stacked Esc/Tab when callbacks supplied. Cancel does NOT
        // replace these — it lives inside the mic Box as a left-aligned
        // overlay during recording, so muscle memory for Esc/Tab is preserved.
        Box(
            modifier = Modifier.width(66.dp).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            when {
                showStackedEscTab -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(
                            6.dp, alignment = Alignment.CenterVertically
                        ),
                        horizontalAlignment = Alignment.Start
                    ) {
                        DictationActionChip(
                            label = "Esc",
                            onClick = onEsc!!,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                        DictationActionChip(
                            label = "Tab",
                            onClick = onTab!!,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                    }
                }
                isRecording -> {
                    // Fallback for callers that don't supply Esc/Tab — the
                    // slot is theirs again so a wide Cancel button is still
                    // reachable.
                    OutlinedButton(
                        onClick = onCancelRecording,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
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
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                strings.cancel.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                else -> {
                    // Empty when no Esc/Tab handlers and not recording.
                }
            }
        }

        // Center: Microphone Button + (during recording) Cancel overlay left
        // of mic. Box stacks them so the mic stays geometrically centered
        // while Cancel hugs the left edge of the box.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording && showStackedEscTab) {
                OutlinedButton(
                    onClick = onCancelRecording,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = strings.cancelDesc,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        strings.cancel.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
                    walkieTalkieMode = walkieTalkieMode,
                    recordingDuration = recordingDuration,
                    transcriptionProgress = transcriptionProgress,
                    processingStage = processingStage,
                    processingPhase = processingPhase,
                    audioFileSize = lastAudioFileSize,
                    audioDurationSeconds = lastAudioDuration,
                    modifier = Modifier
                )

            }
        }

        // Right side: backspace lives here so the right column reads
        // Out → Backspace → Enter top-to-bottom. Repeat-on-hold matches the
        // backspace behaviour in the universal top strip. Hidden when the
        // caller has its own backspace surface (the new Dictation layout
        // moves it to the header column).
        if (showBackspace) {
            Box(
                modifier = Modifier.width(60.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                RepeatableDeleteButton(
                    onDelete = onDelete,
                    onDeleteAll = onDeleteAll,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}
