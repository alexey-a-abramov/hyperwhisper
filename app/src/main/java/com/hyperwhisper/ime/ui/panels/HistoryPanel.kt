package com.hyperwhisper.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.localization.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transcription history panel - fullscreen compact view
 * Shows list of past transcriptions with reprocessing options
 * Audio-only items (failed transcriptions) shown in error color
 */
@Composable
fun TranscriptionHistoryPanel(
    history: List<TranscriptionHistoryItem>,
    onSelect: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    onReprocessWithCurrentSettings: ((TranscriptionHistoryItem) -> Unit)? = null,
    onReprocessWithNewSettings: ((TranscriptionHistoryItem) -> Unit)? = null
) {
    val strings = LocalStrings.current

    // Fullscreen overlay - no padding for maximum space
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Compact header row with title, count, and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and count
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.transcriptionHistory,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            strings.historyCount.replace("{count}", history.size.toString()),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Compact action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (history.isNotEmpty()) {
                        // Clear all - small icon button
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = strings.clearAll,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // History list - fullscreen
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.noHistoryYet,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(history.size) { index ->
                        val item = history[index]
                        val dateTime = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            .format(Date(item.timestamp))
                        val hasAudio = item.audioFilePath != null
                        val hasText = item.text.isNotBlank()
                        val isAudioOnly = hasAudio && !hasText

                        Surface(
                            onClick = { if (hasText) onSelect(item.text) },
                            enabled = hasText,
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                isAudioOnly -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Compact header: timestamp + audio indicator + reprocess buttons inline
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: timestamp and status
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            dateTime,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (hasAudio) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Has audio",
                                                modifier = Modifier.size(12.dp),
                                                tint = if (isAudioOnly) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }
                                        if (isAudioOnly) {
                                            Text(
                                                "Audio only",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Right: compact reprocess buttons (icons only)
                                    if (hasAudio && (onReprocessWithCurrentSettings != null || onReprocessWithNewSettings != null)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (onReprocessWithCurrentSettings != null) {
                                                IconButton(
                                                    onClick = { onReprocessWithCurrentSettings(item) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Replay,
                                                        contentDescription = "Reprocess with current settings",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                            if (onReprocessWithNewSettings != null) {
                                                IconButton(
                                                    onClick = { onReprocessWithNewSettings(item) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = "Reprocess with new settings",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Transcription text (or placeholder for audio-only)
                                if (hasText) {
                                    Text(
                                        item.text,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        lineHeight = 14.sp
                                    )
                                } else if (isAudioOnly) {
                                    Text(
                                        "Tap replay to transcribe this audio",
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
