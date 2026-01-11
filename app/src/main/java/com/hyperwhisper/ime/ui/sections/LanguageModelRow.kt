package com.hyperwhisper.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.ui.buttons.InputLanguageButton
import com.hyperwhisper.ui.buttons.OutputLanguageButton

/**
 * Language and model info row
 * Shows input language, provider/model info (middle), and output language
 * Includes config info button in techie mode
 */
@Composable
fun LanguageModelRow(
    apiSettings: ApiSettings,
    recordingState: RecordingState,
    techieModeEnabled: Boolean,
    onShowInputLanguageDialog: () -> Unit,
    onShowOutputLanguageDialog: () -> Unit,
    onShowConfigInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // Provider/Model Info (MIDDLE) - Shows current transcription mode and model
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Show provider and model
                    val modelDisplay = apiSettings.provider.displayName

                    Text(
                        text = modelDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                    Text(
                        text = apiSettings.modelId,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
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
