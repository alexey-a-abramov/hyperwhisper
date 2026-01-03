package com.hyperwhisper.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hyperwhisper.data.ModelDownloadState
import com.hyperwhisper.data.WhisperModel

/**
 * Composable card for managing local whisper models
 * Shows model list with download/delete controls and storage info
 */
@Composable
fun ModelManagementCard(
    modifier: Modifier = Modifier,
    viewModel: ModelManagementViewModel = hiltViewModel()
) {
    val modelStates by viewModel.modelStates.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Local Models (On-Device)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Download models for offline speech recognition using whisper.cpp",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Storage info
            val totalStorage = viewModel.getTotalStorageUsed()
            if (totalStorage > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Storage",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Total storage used: ${formatBytes(totalStorage)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model list
            WhisperModel.values().forEach { model ->
                val state = modelStates[model] ?: ModelDownloadState.NotDownloaded
                ModelItem(
                    model = model,
                    state = state,
                    onDownload = { viewModel.downloadModel(model) },
                    onDelete = { viewModel.deleteModel(model) },
                    onImportFromFile = { uri -> viewModel.importModelFromFile(model, uri) }
                )
            }
        }
    }
}

/**
 * Individual model item with download/delete/import controls
 */
@Composable
private fun ModelItem(
    model: WhisperModel,
    state: ModelDownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onImportFromFile: (android.net.Uri) -> Unit
) {
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportFromFile(it) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Model name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )

                // Action buttons
                when (state) {
                    is ModelDownloadState.NotDownloaded -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Import from file",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    is ModelDownloadState.Downloaded -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    is ModelDownloadState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    is ModelDownloadState.Error -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Import from file",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Model details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Size: ${model.getFormattedSize()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state) {
                    is ModelDownloadState.Downloaded -> {
                        Text(
                            text = "Ready",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is ModelDownloadState.NotDownloaded -> {
                        Text(
                            text = "Not downloaded",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {}
                }
            }

            // Download progress and URL info
            when (state) {
                is ModelDownloadState.Downloading -> {
                    val context = LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Progress percentage and size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val downloadedMB = state.downloadedBytes / (1024.0 * 1024.0)
                            val totalMB = state.totalBytes / (1024.0 * 1024.0)
                            val progressText = if (state.totalBytes > 0) {
                                "${(state.progress * 100).toInt()}% (${"%.1f".format(downloadedMB)} / ${"%.1f".format(totalMB)} MB)"
                            } else {
                                "${(state.progress * 100).toInt()}%"
                            }
                            Text(
                                text = progressText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Copy URL button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Model URL", model.downloadUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Speed and ETA
                        if (state.speedBytesPerSecond > 0) {
                            val speedMBps = state.speedBytesPerSecond / (1024.0 * 1024.0)
                            val etaMinutes = state.etaSeconds / 60
                            val etaSecondsRem = state.etaSeconds % 60
                            val speedText = "${"%.2f".format(speedMBps)} MB/s"
                            val etaText = if (etaMinutes > 0) {
                                "${etaMinutes}m ${etaSecondsRem}s"
                            } else {
                                "${etaSecondsRem}s"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = speedText,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "ETA",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "ETA: $etaText",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }

                        // Show URL (truncated)
                        Text(
                            text = model.downloadUrl.take(60) + "...",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            lineHeight = 12.sp
                        )
                    }
                }
                is ModelDownloadState.Error -> {
                    val context = LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Error: ${state.message}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error
                        )

                        // Copy URL button for errors too
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Model URL", model.downloadUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy URL",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy URL for debugging", fontSize = 9.sp)
                        }
                    }
                }
                else -> {}
            }

            // Model description
            val description = when (model) {
                WhisperModel.TINY -> "Fastest, lowest accuracy. ~32x realtime on modern devices."
                WhisperModel.BASE -> "Balanced speed and accuracy. ~16x realtime."
                WhisperModel.SMALL -> "Best accuracy, slower. ~6x realtime. For high-end devices."
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Format bytes to human-readable string
 */
private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000) {
        "%.1f GB".format(mb / 1024.0)
    } else {
        "%.0f MB".format(mb)
    }
}
