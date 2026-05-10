package com.hyperwhisper.ui.indicators

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Circular processing indicator for the post-record stage.
 *
 * Visual contract:
 * - Big enough circle (88dp) that the percentage / counter inside the ring
 *   is readable from a glance without leaning in.
 * - Animated wave glyph next to the counter, subtly pulsing — signals
 *   "actively working" so a stalled UI is distinguishable from an idle one.
 * - The counter is prefixed with "≈" and the stage line ends with "approx"
 *   so users don't read the percentage as a hard ETA. Transcription latency
 *   varies a lot with provider load; we avoid promising what we can't keep.
 */
@Composable
fun ProcessingIndicator(
    progress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    onCancel: () -> Unit = {}
) {
    val fileSizeText = when {
        audioFileSize <= 0 -> ""
        audioFileSize < 1024 -> "${audioFileSize}B"
        audioFileSize < 1024 * 1024 -> "${audioFileSize / 1024}KB"
        else -> "${audioFileSize / (1024 * 1024)}MB"
    }
    val durationText = if (audioDurationSeconds > 0) {
        val minutes = (audioDurationSeconds / 60).toInt()
        val seconds = (audioDurationSeconds % 60).toInt()
        if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    } else ""

    // Pulse the wave icon between half- and full-opacity at ~1Hz so it's
    // alive without strobing. RepeatMode.Reverse → smooth in-and-out.
    val transition = rememberInfiniteTransition(label = "wave-pulse")
    val waveAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave-pulse-alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(108.dp),
            contentAlignment = Alignment.Center
        ) {
            if (progress != null && progress > 0f) {
                CircularProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Counter inside the ring: wave icon + percentage. The wave
                // is the "we're alive" cue when progress is approximate.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { alpha = waveAlpha },
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "≈ ${(progress * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // No determinate progress yet — show the wave alone so the
                // user still gets the "alive" cue.
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { alpha = waveAlpha },
                    tint = MaterialTheme.colorScheme.primary
                )
            }

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
                    contentDescription = "Cancel",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (fileSizeText.isNotEmpty() || durationText.isNotEmpty()) {
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
            }
        }

        // Stage label + "approx" suffix so the % isn't read as a hard ETA.
        processingStage?.let { stage ->
            Text(
                text = "${stage.localizedDisplayName()} · approx",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
