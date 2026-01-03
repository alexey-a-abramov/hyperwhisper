package com.hyperwhisper.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    usageStatistics: com.hyperwhisper.data.UsageStatistics,
    onClearStatistics: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val activity = (context as? android.app.Activity)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.aboutHyperWhisper) },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.imeName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "${strings.version} $versionName (${strings.versionCode} $versionCode)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = strings.description,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.features,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = strings.featuresList,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Usage Guide Section
            Text(
                text = "How to Use:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = """Voice Modes:
• Verbatim - Exact transcription as spoken
• Fix Grammar - Corrects grammar and spelling
• Polite - Makes speech professional and friendly
• Prompt Formatter - Optimizes text for AI prompts
• LLM Response - Get direct answers to questions
• Configuration - Control app settings by voice

Configuration Mode:
Switch to "Configuration" mode to control settings by voice commands.
When you speak a command, you'll see a confirmation dialog showing what will change.

Sample Voice Commands:
• "Change input language to Spanish"
• "Switch to English language"
• "Change mode to verbatim"
• "Enable fix grammar mode"
• "Switch to dark mode" / "light mode" / "system theme"
• "Enable history" / "Disable history"
• "Enable developer mode" / "Enable techie mode"
• "Change interface language to Russian"
• "Turn off configuration mode" (returns to verbatim)

How it works:
1. Select "Configuration" from the mode selector
2. Tap the microphone and speak your command
3. Review the change in the confirmation dialog
4. Tap "Apply Change" to confirm or "Cancel" to dismiss

After confirmation, you'll see a notification that the setting was updated.

Delete Button:
• Tap - Delete one character (or selected text)
• Hold - Repeat delete continuously
• Hold 5 seconds - Delete ALL text (button turns red)

Language Selection:
Use Configuration mode to change languages hands-free, or tap the language buttons below the microphone to select from the list.""",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Usage Statistics Section
            Text(
                text = strings.usageStatisticsAndCosts,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            if (usageStatistics.modelUsage.isEmpty() && usageStatistics.totalAudioSeconds == 0.0) {
                Text(
                    text = strings.noUsageDataYet,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Overall Summary Card
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Total audio
                        val audioMinutes = (usageStatistics.totalAudioSeconds / 60).toInt()
                        val audioSeconds = (usageStatistics.totalAudioSeconds % 60).toInt()
                        Text(
                            text = "${strings.totalAudio}: $audioMinutes${strings.minutes} $audioSeconds${strings.seconds}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Total cost calculation
                        var totalCost = 0.0
                        usageStatistics.modelUsage.forEach { (modelId, usage) ->
                            totalCost += com.hyperwhisper.data.calculateCost(
                                modelId = modelId,
                                inputTokens = usage.inputTokens,
                                outputTokens = usage.outputTokens,
                                audioSeconds = if (modelId.contains("whisper", ignoreCase = true))
                                    usageStatistics.totalAudioSeconds else 0.0
                            )
                        }

                        Text(
                            text = "${strings.estimatedTotalCost}: $${String.format("%.4f", totalCost)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Per-Model Breakdown
                Text(
                    text = strings.modelBreakdown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                usageStatistics.modelUsage.entries.sortedByDescending { it.value.totalTokens }.forEach { (modelId, usage) ->
                    val modelCost = com.hyperwhisper.data.calculateCost(
                        modelId = modelId,
                        inputTokens = usage.inputTokens,
                        outputTokens = usage.outputTokens,
                        audioSeconds = if (modelId.contains("whisper", ignoreCase = true))
                            usageStatistics.totalAudioSeconds else 0.0
                    )

                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Model name and cost
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = modelId,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$${String.format("%.4f", modelCost)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (modelCost > 0.01)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Token details (if available)
                            if (usage.totalTokens > 0) {
                                Text(
                                    text = "${strings.inputTokens}: ${formatNumber(usage.inputTokens)} tokens",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${strings.outputTokens}: ${formatNumber(usage.outputTokens)} tokens",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${strings.totalTokens}: ${formatNumber(usage.totalTokens)} tokens",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Audio-only model (like Whisper)
                                Text(
                                    text = strings.audioBasedPricing,
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pricing note
                Text(
                    text = strings.costsEstimateNote,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Clear statistics button
                androidx.compose.material3.TextButton(onClick = onClearStatistics) {
                    Text(strings.clearStatistics, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Format large numbers with commas for readability
 */
private fun formatNumber(number: Long): String {
    return "%,d".format(number)
}
