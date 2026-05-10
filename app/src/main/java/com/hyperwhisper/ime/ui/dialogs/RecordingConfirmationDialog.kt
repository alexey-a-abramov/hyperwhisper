package com.hyperwhisper.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirmation dialog shown when recording duration exceeds 30 seconds
 * Prevents accidental data loss for long recordings
 */
@Composable
fun RecordingConfirmationDialog(
    durationSeconds: Long,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    val minutes = (durationSeconds / 60)
    val seconds = (durationSeconds % 60)
    val durationText = if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }

    AlertDialog(
        onDismissRequest = { /* Don't dismiss on outside click - require explicit choice */ },
        title = {
            Text(
                text = "Process Recording?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You recorded $durationText of audio.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Do you want to process this recording or discard it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "This confirmation helps prevent accidental data loss for longer recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text("Process Recording")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard
            ) {
                Text("Discard")
            }
        }
    )
}
