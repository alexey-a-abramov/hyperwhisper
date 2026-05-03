package com.hyperwhisper.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.SUPPORTED_LANGUAGES
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.util.localizedDisplayName

/**
 * Configuration info overlay
 * Shows current API settings and usage statistics
 * Displays provider, model, languages, endpoints, API key (masked)
 */
@Composable
fun ConfigInfoDialog(
    apiSettings: com.hyperwhisper.data.ApiSettings,
    usageStatistics: com.hyperwhisper.data.UsageStatistics,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    // Full-screen overlay within keyboard (not a separate Dialog window)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 16.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        strings.currentConfiguration,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider()

                // Content (scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Provider
                    ConfigInfoItem(
                        label = strings.provider,
                        value = apiSettings.provider.localizedDisplayName()
                    )

                    // Transcription Model
                    ConfigInfoItem(
                        label = strings.transcriptionModel,
                        value = apiSettings.modelId
                    )

                    // Post-Processing Model
                    ConfigInfoItem(
                        label = strings.postProcessingModel,
                        value = strings.postProcessingModelDesc
                    )

                    // Endpoint
                    ConfigInfoItem(
                        label = "Base URL",
                        value = apiSettings.baseUrl,
                        smallText = true
                    )

                    // Input Language
                    ConfigInfoItem(
                        label = "Input Language (Speech)",
                        value = if (apiSettings.inputLanguage.isEmpty()) {
                            "Auto-detect"
                        } else {
                            val lang = SUPPORTED_LANGUAGES.find { it.code == apiSettings.inputLanguage }
                            "${lang?.name ?: apiSettings.inputLanguage} (${apiSettings.inputLanguage})"
                        }
                    )

                    // Output Language
                    ConfigInfoItem(
                        label = "Output Language (Translation)",
                        value = if (apiSettings.outputLanguage.isEmpty()) {
                            "None (keep original)"
                        } else {
                            val lang = SUPPORTED_LANGUAGES.find { it.code == apiSettings.outputLanguage }
                            "${lang?.name ?: apiSettings.outputLanguage} (${apiSettings.outputLanguage})"
                        }
                    )

                    // API Key
                    ConfigInfoItem(
                        label = "API Key",
                        value = if (apiSettings.getCurrentApiKey().isEmpty()) "Not configured"
                        else "${apiSettings.getCurrentApiKey().take(10)}${"*".repeat(20)}",
                        smallText = true
                    )
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        "CLOSE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigInfoItem(
    label: String,
    value: String,
    smallText: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            fontSize = if (smallText) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = if (smallText) 16.sp else 18.sp
        )
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}
