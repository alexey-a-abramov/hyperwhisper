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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onPlayAudio: ((TranscriptionHistoryItem) -> Unit)? = null,
    onReprocessWithCurrentSettings: ((TranscriptionHistoryItem) -> Unit)? = null,
    onReprocessWithNewSettings: ((TranscriptionHistoryItem) -> Unit)? = null
) {
    val strings = LocalStrings.current
    // Local confirmation state — IMEs can't host real Dialogs, so the
    // confirmation is rendered as an inline overlay over the history list.
    var showClearConfirmation by remember { mutableStateOf(false) }

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
                        // Clear all — confirmation gate so a stray tap can't
                        // wipe the entire history. Tap once to arm the
                        // confirmation overlay below.
                        IconButton(
                            onClick = { showClearConfirmation = true },
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

                                    // Right: just the play button in the
                                    // header. The reprocess buttons are now
                                    // a labeled row below the text — bigger
                                    // hit targets and discoverable instead of
                                    // hiding behind 16dp icons.
                                    if (hasAudio && onPlayAudio != null) {
                                        IconButton(
                                            onClick = { onPlayAudio(item) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play original audio",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
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
                                        "Tap reprocess to transcribe this audio",
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                // Reprocess actions — only meaningful when
                                // we still have the source audio. Two paths:
                                // current settings (one tap), or pick a
                                // different model (opens picker).
                                if (hasAudio && (onReprocessWithCurrentSettings != null || onReprocessWithNewSettings != null)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (onReprocessWithCurrentSettings != null) {
                                            FilledTonalButton(
                                                onClick = { onReprocessWithCurrentSettings(item) },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                ),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Replay,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.size(4.dp))
                                                Text(
                                                    "Redo: current",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (onReprocessWithNewSettings != null) {
                                            OutlinedButton(
                                                onClick = { onReprocessWithNewSettings(item) },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.size(4.dp))
                                                Text(
                                                    "Redo: pick model",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
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
        }

        // Confirmation overlay — clearing history is destructive (deletes
        // every transcription + audio file), so a stray tap on the trash
        // icon shouldn't be enough. Inline Surface instead of a real Dialog
        // because the IME can't host one.
        if (showClearConfirmation) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Clear all history?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This deletes ${history.size} transcription${if (history.size == 1) "" else "s"} and any saved audio. This can't be undone.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showClearConfirmation = false },
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(strings.cancel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = {
                                    onClearAll()
                                    showClearConfirmation = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Text(
                                    strings.clearAll,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
