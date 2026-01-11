package com.hyperwhisper.ui.settings.sections

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AppUpdateSection(
    updateManager: com.hyperwhisper.ime.update.UpdateManager?,
    onShowUpdateDialog: (com.hyperwhisper.ime.update.UpdateInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Version",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = updateManager?.getCurrentVersion()?.second ?: "Unknown",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            when (val result = updateManager?.checkForUpdates()) {
                                is com.hyperwhisper.ime.update.UpdateCheckResult.UpdateAvailable -> {
                                    onShowUpdateDialog(result.updateInfo)
                                }
                                is com.hyperwhisper.ime.update.UpdateCheckResult.NoUpdateAvailable -> {
                                    Toast.makeText(
                                        context,
                                        "Already up to date!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is com.hyperwhisper.ime.update.UpdateCheckResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        "Update check failed: ${result.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                null -> {}
                            }
                        }
                    },
                    contentPadding = PaddingValues(8.dp, 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check for Updates",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Check", fontSize = 12.sp)
                }
            }
        }
    }
}
