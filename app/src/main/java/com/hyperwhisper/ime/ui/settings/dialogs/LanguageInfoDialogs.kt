package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InputLanguageInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Input Language Hint")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This setting provides a hint to the speech recognition model about the language being spoken. While 'Auto-detect' works well in most cases, providing a specific language can improve accuracy:",
                    fontSize = 14.sp
                )
                Text("• For speakers with strong accents.", fontSize = 14.sp)
                Text("• For less common languages or dialects.", fontSize = 14.sp)
                Text("• In noisy environments.", fontSize = 14.sp)
                Divider()
                Text(
                    "If your transcriptions are inaccurate, try setting this to your native language.",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}

@Composable
fun LogsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("View API Logs")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "HyperWhisper logs all API requests and responses for debugging.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Divider()

                Text(
                    "Viewing Logs:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text("• Use ADB: adb logcat | grep HyperWhisper", fontSize = 13.sp)
                Text("• Install a logcat app from Play Store", fontSize = 13.sp)
                Text("• Filter by: ChatCompletionStrategy, VoiceRepository", fontSize = 13.sp)

                Divider()

                Text(
                    "Logged Information:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text("• API request details (URL, model, prompts)", fontSize = 13.sp)
                Text("• Response status and content", fontSize = 13.sp)
                Text("• Token usage (input/output/total)", fontSize = 13.sp)
                Text("• Audio file information", fontSize = 13.sp)
                Text("• Error messages and traces", fontSize = 13.sp)

                Divider()

                Text(
                    "Note: Logs show first 10 chars of API keys only.",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}
