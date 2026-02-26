package com.hyperwhisper.ui.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.ui.buttons.InputLanguageButton
import com.hyperwhisper.ui.buttons.OutputLanguageButton

/**
 * Language and model info row
 * Shows input language, mode button, model/provider button (middle), and output language
 * Includes config info button in techie mode
 */
@Composable
fun LanguageModelRow(
    apiSettings: ApiSettings,
    recordingState: RecordingState,
    techieModeEnabled: Boolean,
    voiceModes: List<VoiceMode>,
    selectedModeId: String,
    onShowInputLanguageDialog: () -> Unit,
    onShowOutputLanguageDialog: () -> Unit,
    onShowConfigInfo: () -> Unit,
    onShowProviderModelDialog: () -> Unit,
    onShowModeDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedMode = remember(voiceModes, selectedModeId) {
        voiceModes.firstOrNull { it.id == selectedModeId }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Input Language Selector (LEFT)
        InputLanguageButton(
            currentLanguage = apiSettings.inputLanguage,
            onClick = onShowInputLanguageDialog,
            enabled = recordingState == RecordingState.IDLE
        )

        // Mode Button (MIDDLE LEFT) - Clickable to open mode selection
        Surface(
            onClick = {
                if (recordingState == RecordingState.IDLE) {
                    onShowModeDialog()
                }
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (recordingState == RecordingState.IDLE) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            border = BorderStroke(
                1.5.dp,
                if (recordingState == RecordingState.IDLE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            ),
            enabled = recordingState == RecordingState.IDLE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedMode?.name ?: "Select Mode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (recordingState == RecordingState.IDLE) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    maxLines = 1
                )
            }
        }

        // Provider/Model Info Button (MIDDLE RIGHT) - Shows provider and model
        Surface(
            onClick = {
                if (recordingState == RecordingState.IDLE) {
                    onShowProviderModelDialog()
                }
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            color = if (recordingState == RecordingState.IDLE) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.5.dp,
                if (recordingState == RecordingState.IDLE) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            ),
            enabled = recordingState == RecordingState.IDLE
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apiSettings.provider.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (recordingState == RecordingState.IDLE) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        maxLines = 1
                    )
                    Text(
                        text = apiSettings.modelId,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        color = if (recordingState == RecordingState.IDLE) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        maxLines = 1
                    )
                }

                // Config info button (only shown in techie mode)
                if (techieModeEnabled) {
                    IconButton(
                        onClick = onShowConfigInfo,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Configuration Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Output Language Selector (RIGHT)
        OutputLanguageButton(
            currentLanguage = apiSettings.outputLanguage,
            onClick = onShowOutputLanguageDialog,
            enabled = recordingState == RecordingState.IDLE
        )
    }
}
