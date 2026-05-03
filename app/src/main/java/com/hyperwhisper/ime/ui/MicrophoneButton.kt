package com.hyperwhisper.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.localizedDisplayName

@Composable
fun MicrophoneButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelTranscription: () -> Unit = {},
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
                IdleMicButton(onClick = onStartRecording)
            }
            RecordingState.RECORDING -> {
                RecordingMicButton(
                    onClick = onStopRecording,
                    recordingDuration = recordingDuration
                )
            }
            RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> {
                // Show awaiting confirmation button
                RecordingMicButton(
                    onClick = onStopRecording,
                    recordingDuration = recordingDuration
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
                IdleMicButton(onClick = onStartRecording)
            }
        }
    }
}

@Composable
fun IdleMicButton(onClick: () -> Unit) {
    val strings = LocalStrings.current
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = strings.startRecording,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun RecordingMicButton(onClick: () -> Unit, recordingDuration: Long = 0L) {
    val strings = LocalStrings.current
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Calculate minutes and seconds
    val seconds = (recordingDuration / 1000) % 60
    val minutes = (recordingDuration / 1000) / 60
    val timeText = "$minutes:${seconds.toString().padStart(2, '0')}"

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .scale(scale),
        containerColor = Color(0xFFE53935), // Red
        contentColor = Color.White
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = strings.stopRecording,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = timeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ProcessingIndicator(
    progress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    onCancel: () -> Unit = {}
) {
    val strings = LocalStrings.current
    // Format file size for display
    val fileSizeText = when {
        audioFileSize < 1024 -> "${audioFileSize}B"
        audioFileSize < 1024 * 1024 -> "${audioFileSize / 1024}KB"
        else -> "${audioFileSize / (1024 * 1024)}MB"
    }

    // Format duration for display
    val durationText = if (audioDurationSeconds > 0) {
        val minutes = (audioDurationSeconds / 60).toInt()
        val seconds = (audioDurationSeconds % 60).toInt()
        if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    } else ""

    // Estimate time based on file size (rough estimate: ~1KB/sec processing)
    val estimatedSeconds = (audioFileSize / 1024.0).toInt().coerceAtLeast(1)
    val estimatedText = when {
        audioFileSize > 0 -> String.format(strings.processingEstimatedTimeFormat, estimatedSeconds)
        else -> ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Progress indicator with percentage
            if (progress != null && progress > 0f) {
                // Background circle (full)
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                // Actual progress
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Show percentage text (large and prominent)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Indeterminate progress
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Show "Processing..." text
                Text(
                    text = "...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Cancel button (clickable overlay)
            FloatingActionButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.cancel,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Show file info in small text
        if (fileSizeText.isNotEmpty() || durationText.isNotEmpty() || estimatedText.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (fileSizeText.isNotEmpty()) {
                    Text(
                        text = fileSizeText,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (durationText.isNotEmpty()) {
                    Text(
                        text = "• $durationText",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (estimatedText.isNotEmpty()) {
                    Text(
                        text = "• $estimatedText",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Show processing stage text below file info (prominent)
        processingStage?.let { stage ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stage.localizedDisplayName(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
