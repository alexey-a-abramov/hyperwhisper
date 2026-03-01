package com.hyperwhisper.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.ime.update.UpdateProbeDetails
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    buildDate: String = "",
    usageStatistics: com.hyperwhisper.data.UsageStatistics,
    techieModeEnabled: Boolean = false,
    integrationResults: List<ProviderIntegrationResult> = emptyList(),
    integrationRunning: Boolean = false,
    updateProbeDetails: UpdateProbeDetails? = null,
    updateManager: com.hyperwhisper.ime.update.UpdateManager? = null,
    onClearStatistics: () -> Unit,
    onRunIntegrationTests: () -> Unit = {},
    onOpenProviderConfiguration: (com.hyperwhisper.data.ApiProvider) -> Unit = {},
    onRefreshUpdateProbe: (() -> Unit)? = null,
    onShowUpdateDialog: (com.hyperwhisper.ime.update.UpdateInfo) -> Unit = {}
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

            // Version info with build date
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${strings.version} $versionName (${strings.versionCode}: $versionCode)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                if (buildDate.isNotEmpty()) {
                    Text(
                        text = "Built: $buildDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // APK location - very subtle
                if (updateProbeDetails != null) {
                    Text(
                        text = updateProbeDetails.installedApkPath,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                text = "${strings.usageGuideTitle}:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = """${strings.voiceModesGuide}:
• ${strings.voiceModeVerbatimDesc}
• ${strings.voiceModeFixGrammarDesc}
• ${strings.voiceModePoliteDesc}
• ${strings.voiceModePromptFormatterDesc}
• ${strings.voiceModeLlmResponseDesc}
• ${strings.voiceModeConfigurationDesc}

${strings.configurationModeTitle}:
${strings.configurationModeDesc}

${strings.sampleVoiceCommandsTitle}:
• ${strings.sampleCommandLanguage}
• ${strings.sampleCommandMode}
• ${strings.sampleCommandTheme}
• ${strings.sampleCommandHistory}
• ${strings.sampleCommandDeveloper}
• ${strings.sampleCommandInterface}
• ${strings.sampleCommandExit}

${strings.howItWorksTitle}:
${strings.howItWorksStep1}
${strings.howItWorksStep2}
${strings.howItWorksStep3}
${strings.howItWorksStep4}

${strings.howItWorksConfirmation}

${strings.deleteButtonTitle}:
• ${strings.deleteButtonTap}
• ${strings.deleteButtonHold}
• ${strings.deleteButtonLongHold}

${strings.languageSelectionTitle}:
${strings.languageSelectionDesc}""",
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

            // Update Probe Details Section - at the bottom
            if (updateProbeDetails != null) {
                Spacer(modifier = Modifier.height(16.dp))
                UpdateProbeSection(
                    probeDetails = updateProbeDetails,
                    onRefresh = onRefreshUpdateProbe
                )
            }

            if (techieModeEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                IntegrationTestSection(
                    running = integrationRunning,
                    results = integrationResults,
                    onRun = onRunIntegrationTests,
                    onOpenProviderConfiguration = onOpenProviderConfiguration
                )
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

/**
 * Update probe details section
 */
@Composable
private fun UpdateProbeSection(
    probeDetails: UpdateProbeDetails,
    onRefresh: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Update Check",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onRefresh != null) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Current build info
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Installed Build",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Built: ${probeDetails.currentBuildDate}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Timestamp: ${probeDetails.currentBuildTimestamp}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // GitHub Release check
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GitHub Releases",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (probeDetails.githubReleaseChecked) {
                            Icon(
                                imageVector = if (probeDetails.githubReleaseVersion != null) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.Close
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (probeDetails.githubReleaseVersion != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    if (probeDetails.githubReleaseVersion != null) {
                        Text(
                            text = "Latest: v${probeDetails.githubReleaseVersion}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = probeDetails.githubReleaseUrl ?: "",
                            fontSize = 7.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (probeDetails.githubReleaseError != null) {
                        Text(
                            text = probeDetails.githubReleaseError,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "No releases found",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Divider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            // Probe locations
            Text(
                text = "Local APK Locations:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            probeDetails.probeResults.forEach { result ->
                ProbeResultItem(result = result)
            }

            // Summary
            if (probeDetails.updateAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Update available",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual probe result item
 */
@Composable
private fun ProbeResultItem(result: com.hyperwhisper.ime.update.ApkProbeResult) {
    val statusColor = when {
        result.isNewer -> MaterialTheme.colorScheme.primary
        result.exists && result.readable -> MaterialTheme.colorScheme.tertiary
        result.exists -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    val statusIcon = when {
        result.isNewer -> Icons.Default.Check
        result.exists && result.readable -> Icons.Default.Close
        result.exists -> Icons.Default.Warning
        else -> Icons.Default.Close
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Path and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.displayPath,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = statusColor
                )
            }

            // Details if file exists
            if (result.exists && result.readable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = result.fileModifiedDate,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${result.fileSizeFormatted} | ${result.timeDifferenceFormatted}",
                        fontSize = 8.sp,
                        color = if (result.isNewer) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        fontWeight = if (result.isNewer) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Status message
                if (result.isNewer) {
                    Text(
                        text = "NEWER - Update available",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (result.errorMessage != null) {
                    Text(
                        text = result.errorMessage,
                        fontSize = 8.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            } else if (!result.exists) {
                Text(
                    text = "Not found",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            } else if (result.errorMessage != null) {
                Text(
                    text = result.errorMessage,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun IntegrationTestSection(
    running: Boolean,
    results: List<ProviderIntegrationResult>,
    onRun: () -> Unit,
    onOpenProviderConfiguration: (com.hyperwhisper.data.ApiProvider) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Provider Integration Tests (Techie)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = onRun,
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Running...")
                } else {
                    Text("Run Integration Tests")
                }
            }

            if (results.isNotEmpty()) {
                Divider()
                results.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.provider.displayName,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (result.configured) "Configured" else "Not configured",
                            fontSize = 10.sp,
                            color = if (result.configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = if (result.success) "Available" else "Unavailable",
                            fontSize = 11.sp,
                            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = " ${result.durationMs}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = result.message + (result.statusCode?.let { " (HTTP $it)" } ?: ""),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onOpenProviderConfiguration(result.provider) }) {
                        Text("Open provider configuration")
                    }
                }
            }
        }
    }
}
