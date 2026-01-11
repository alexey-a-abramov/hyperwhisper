package com.hyperwhisper.ui.indicators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ProcessingStage

@Composable
fun ProcessingIndicator(
    progress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    onCancel: () -> Unit = {}
) {
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
        audioFileSize > 0 -> "Est: ~${estimatedSeconds}s"
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
            // Progress indicator
            if (progress != null && progress > 0f) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(60.dp),
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Show percentage text
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // Indeterminate progress
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    strokeWidth = 5.dp,
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
                    contentDescription = "Cancel",
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

        // Show processing stage text below file info
        processingStage?.let { stage ->
            Text(
                text = stage.displayName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
