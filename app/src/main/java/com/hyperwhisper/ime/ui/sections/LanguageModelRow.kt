package com.hyperwhisper.ui.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.ui.buttons.InputLanguageButton
import com.hyperwhisper.ui.buttons.OutputLanguageButton
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Language and model info row.
 *
 * Order: Input language → Transcribing model → Voice mode → (LLM model, when
 * the mode uses post-processing) → Output language.
 *
 * The voice-mode chip is intentionally narrow — its labels are short
 * ("Verbatim", "Polish") and a wide chip wastes the row. The LLM chip only
 * shows when the selected mode runs a post-processing step (i.e.
 * `processingMode != "direct"`); for verbatim transcription there's no LLM
 * involved so the chip would be a lie.
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
    onShowLlmModelDialog: () -> Unit = onShowModeDialog,
    modifier: Modifier = Modifier
) {
    val selectedMode = remember(voiceModes, selectedModeId) {
        voiceModes.firstOrNull { it.id == selectedModeId }
    }
    val idle = recordingState == RecordingState.IDLE
    val usesLlm = selectedMode != null && selectedMode.processingMode != "direct"

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Input language (LEFT)
        InputLanguageButton(
            currentLanguage = apiSettings.inputLanguage,
            onClick = onShowInputLanguageDialog,
            enabled = idle
        )

        // Transcribing provider + model. Provider is the primary line; the
        // model id sits underneath in lighter weight so the user can see at
        // a glance which exact endpoint is wired up.
        Surface(
            onClick = { if (idle) onShowProviderModelDialog() },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            color = if (idle)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.5.dp,
                if (idle) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            enabled = idle
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLocal = apiSettings.localModelSettings.useLocalWhisper
                val providerLabel = if (isLocal) "Local Whisper" else apiSettings.provider.localizedDisplayName()
                val modelLabel = if (isLocal) {
                    apiSettings.localModelSettings.whisperModelPath
                        .substringAfterLast('/')
                        .ifBlank { "no model" }
                } else {
                    apiSettings.modelId.ifBlank { "no model" }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = providerLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (idle) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = modelLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        color = if (idle) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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

        // Voice mode chip — narrow, fixed width, just the mode label.
        Surface(
            onClick = { if (idle) onShowModeDialog() },
            modifier = Modifier
                .width(if (usesLlm) 80.dp else 110.dp)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (idle) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.5.dp,
                if (idle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            enabled = idle
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedMode?.name ?: "Select Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (idle) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }

        // LLM post-processing model chip — only when the active mode actually
        // runs a second-pass LLM. For verbatim transcription this is hidden
        // since there's no LLM to configure.
        if (usesLlm) {
            Surface(
                onClick = { if (idle) onShowLlmModelDialog() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                color = if (idle)
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (idle) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                enabled = idle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = if (idle) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apiSettings.llmConfig.provider.localizedDisplayName(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (idle) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = apiSettings.llmConfig.modelId.ifBlank { "no model" },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Light,
                            color = if (idle) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Output language (RIGHT)
        OutputLanguageButton(
            currentLanguage = apiSettings.outputLanguage,
            onClick = onShowOutputLanguageDialog,
            enabled = idle
        )
    }
}
