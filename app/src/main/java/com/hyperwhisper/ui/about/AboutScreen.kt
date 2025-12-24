package com.hyperwhisper.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    usageStatistics: com.hyperwhisper.data.UsageStatistics,
    onClearStatistics: () -> Unit
) {
    val context = LocalContext.current
    val activity = (context as? android.app.Activity)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About HyperWhisper") },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "HyperWhisper IME",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Version $versionName (Code $versionCode)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "HyperWhisper is a voice-to-text input method (keyboard) that uses advanced speech recognition APIs to provide fast and accurate transcriptions. It is designed for developers and power users who want to customize their voice input experience.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Features:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "• Customizable API provider (OpenAI, Groq, OpenRouter, etc.)" +
                       "\n• Multiple voice modes (Verbatim, Grammar Fix, Polite, etc.)" +
                       "\n• Support for different input and output languages" +
                       "\n• Modern, responsive UI built with Jetpack Compose",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Usage Statistics Section
            Text(
                text = "Usage Statistics:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            if (usageStatistics.modelUsage.isEmpty() && usageStatistics.totalAudioSeconds == 0.0) {
                Text(
                    text = "No usage data yet. Start using the keyboard to see statistics!",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Audio duration
                val audioMinutes = (usageStatistics.totalAudioSeconds / 60).toInt()
                val audioSeconds = (usageStatistics.totalAudioSeconds % 60).toInt()
                Text(
                    text = "Total Audio Processed: ${audioMinutes}m ${audioSeconds}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Token usage by model
                Text(
                    text = "Token Usage by Model:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                usageStatistics.modelUsage.forEach { (modelId, usage) ->
                    Text(
                        text = "  • $modelId:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "    In: ${formatNumber(usage.inputTokens)} | Out: ${formatNumber(usage.outputTokens)} | Total: ${formatNumber(usage.totalTokens)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clear statistics button
                androidx.compose.material3.TextButton(onClick = onClearStatistics) {
                    Text("CLEAR STATISTICS", fontSize = 12.sp)
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
