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
import com.hyperwhisper.localization.LocalStrings

@Composable
fun InputLanguageInfoDialog(onDismiss: () -> Unit) {
    val strings = LocalStrings.current
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
                Text(strings.inputLanguageHintLabel)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    strings.dialogInputLanguageBody,
                    fontSize = 14.sp
                )
                Text(strings.dialogInputLanguageBullet1, fontSize = 14.sp)
                Text(strings.dialogInputLanguageBullet2, fontSize = 14.sp)
                Text(strings.dialogInputLanguageBullet3, fontSize = 14.sp)
                Divider()
                Text(
                    strings.dialogInputLanguageFooter,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close.uppercase())
            }
        }
    )
}

@Composable
fun LogsInfoDialog(onDismiss: () -> Unit) {
    val strings = LocalStrings.current
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
                Text(strings.viewApiLogs)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    strings.dialogLogsInfoBody,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Divider()

                Text(
                    strings.dialogLogsInfoViewingHeader,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(strings.dialogLogsInfoViewing1, fontSize = 13.sp)
                Text(strings.dialogLogsInfoViewing2, fontSize = 13.sp)
                Text(strings.dialogLogsInfoViewing3, fontSize = 13.sp)

                Divider()

                Text(
                    strings.dialogLogsInfoLoggedHeader,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(strings.dialogLogsInfoLogged1, fontSize = 13.sp)
                Text(strings.dialogLogsInfoLogged2, fontSize = 13.sp)
                Text(strings.dialogLogsInfoLogged3, fontSize = 13.sp)
                Text(strings.dialogLogsInfoLogged4, fontSize = 13.sp)
                Text(strings.dialogLogsInfoLogged5, fontSize = 13.sp)

                Divider()

                Text(
                    strings.dialogLogsInfoNote,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close.uppercase())
            }
        }
    )
}
