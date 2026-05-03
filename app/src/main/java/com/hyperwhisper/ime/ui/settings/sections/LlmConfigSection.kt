package com.hyperwhisper.ui.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.ui.settings.ConnectionTestState
import com.hyperwhisper.ui.settings.TestLogEntry
import com.hyperwhisper.ui.settings.components.selectors.LlmProviderSelector
import com.hyperwhisper.ui.settings.components.selectors.LlmModelSelector

@Composable
fun LlmConfigSection(
    llmProvider: LlmProvider,
    llmBaseUrl: String,
    llmApiKey: String,
    llmRequiresAuth: Boolean,
    llmModelId: String,
    providerApiKey: String = "", // For conditional "reuse" button
    onLlmProviderChange: (LlmProvider) -> Unit,
    onLlmBaseUrlChange: (String) -> Unit,
    onLlmApiKeyChange: (String) -> Unit,
    onLlmRequiresAuthChange: (Boolean) -> Unit,
    onLlmModelIdChange: (String) -> Unit,
    onResetLlmDefaults: () -> Unit,
    onReuseLlmKeyForProvider: () -> Unit,
    onShowLlmInfo: () -> Unit,
    postProcessingTestState: ConnectionTestState = ConnectionTestState.Idle,
    postProcessingTestLog: List<TestLogEntry> = emptyList(),
    onTestPostProcessing: () -> Unit = {},
    onResetPostProcessingTestState: () -> Unit = {},
    openRouterModels: List<com.hyperwhisper.ui.settings.OpenRouterModelInfo> = emptyList(),
    openRouterRefreshing: Boolean = false,
    openRouterError: String? = null,
    onRefreshOpenRouterModels: () -> Unit = {},
    localGemmaModelPath: String = "",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
    // LLM Provider selector
    LlmProviderSelector(
        selectedProvider = llmProvider,
        onProviderSelected = onLlmProviderChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    )

    // Local Gemma in-process explainer. Hides the now-misleading base URL
    // field and tells the user where the model file is configured.
    if (llmProvider == LlmProvider.NONE) {
        // No-op
    } else if (llmProvider == LlmProvider.LOCAL_LLAMACPP) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Local llama.cpp HTTP server",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Start the server in Termux first, then HyperWhisper hits it over HTTP. " +
                        "Default URL assumes llama-server's zero-config 127.0.0.1:8080.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Termux:  pkg install llama-cpp\n" +
                        "         llama-server -m /sdcard/LLM/<model>.gguf",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Model ID below must match llama-server's reported model name " +
                        "(usually the GGUF file stem, or whatever you pass to --alias).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    } else if (llmProvider == LlmProvider.LOCAL_GEMMA) {
        val active = localGemmaModelPath.isNotBlank() &&
            java.io.File(localGemmaModelPath).exists()
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (active) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "In-process MediaPipe LLM",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    if (active)
                        "Model: ${java.io.File(localGemmaModelPath).name}"
                    else
                        "No Gemma model selected. Pick a MediaPipe-converted .bin (litert-community on HuggingFace) under Transcription → Local. GGUF files won't load.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Runs entirely on-device. No external server needed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (active) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.8f)
                )
            }
        }
    }

    // Only show configuration if not NONE
    if (llmProvider != LlmProvider.NONE) {
        // Base URL with reset button — hidden for in-process providers since
        // they don't use HTTP at all.
        if (llmProvider != LlmProvider.LOCAL_GEMMA) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = llmBaseUrl,
                    onValueChange = onLlmBaseUrlChange,
                    label = { Text("LLM Base URL") },
                    placeholder = { Text(llmProvider.defaultEndpoint) },
                    supportingText = { Text("API endpoint for post-processing LLM") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = onResetLlmDefaults,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("RESET")
                }
            }
        }

        Spacer(Modifier.padding(vertical = 8.dp))

        // No API Key checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = !llmRequiresAuth,
                onCheckedChange = { onLlmRequiresAuthChange(!it) }
            )
            Text(
                text = "No API key required",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.padding(vertical = 8.dp))

        // API Key (only shown if auth is required)
        if (llmRequiresAuth) {
            OutlinedTextField(
                value = llmApiKey,
                onValueChange = onLlmApiKeyChange,
                label = { Text("LLM API Key") },
                placeholder = { Text("Enter API key for LLM provider") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Pull from transcription side: when the active LLM provider has
            // a sibling configured for transcription with a stored key,
            // surface a one-tap "use that key" affordance so the user doesn't
            // have to retype it.
            if (llmApiKey.isBlank() && providerApiKey.isNotBlank()) {
                Spacer(Modifier.padding(vertical = 4.dp))
                OutlinedButton(
                    onClick = onReuseLlmKeyForProvider,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use this provider's key from Transcription")
                }
            } else if (llmApiKey.isNotBlank() && providerApiKey.isBlank()) {
                // Reverse direction: push LLM key into transcription side
                // when transcription is missing one. Same handler — caller
                // decides which direction to wire based on emptiness.
                Spacer(Modifier.padding(vertical = 4.dp))
                OutlinedButton(
                    onClick = onReuseLlmKeyForProvider,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reuse this key for transcription provider")
                }
            }

            Spacer(Modifier.padding(vertical = 8.dp))
        }

        // Model selector with info button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LlmModelSelector(
                selectedModel = llmModelId,
                availableModels = llmProvider.defaultModels,
                showFreeFilter = llmProvider == LlmProvider.OPENROUTER,
                onModelSelected = onLlmModelIdChange,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onShowLlmInfo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "LLM Model Info",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.padding(vertical = 8.dp))

        // OpenRouter free-models discovery — same panel as the transcription
        // side, but with the audio filter OFF by default since LLM
        // post-processing wants chat-capable models, not audio ones.
        if (llmProvider == LlmProvider.OPENROUTER) {
            OpenRouterDiscoveryPanel(
                models = openRouterModels,
                refreshing = openRouterRefreshing,
                error = openRouterError,
                selectedModelId = llmModelId,
                onRefresh = onRefreshOpenRouterModels,
                onSelect = onLlmModelIdChange,
                audioFilterDefault = false,
                audioFilterLabel = "Audio-capable only",
                descriptionText = "Browse the OpenRouter catalog. Filter for " +
                    "free models; tap any row to use it for post-processing."
            )
            Spacer(Modifier.padding(vertical = 8.dp))
        }

        // Info text
        Text(
            text = "This LLM is used for post-processing (polite, casual, etc.) and translation. Not used in verbatim mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Test post-processing button
        Button(
            onClick = onTestPostProcessing,
            modifier = Modifier.fillMaxWidth(),
            enabled = postProcessingTestState !is ConnectionTestState.Testing &&
                llmModelId.isNotBlank() &&
                (!llmRequiresAuth || llmApiKey.isNotBlank())
        ) {
            Text("Test post-processing with sample text")
        }

        com.hyperwhisper.ui.settings.sections.TestLogPanel(
            entries = postProcessingTestLog,
            state = postProcessingTestState,
            autoCloseOnSuccess = true,
            onDismiss = onResetPostProcessingTestState,
            runningPlaceholder = "Sending sample text to LLM…"
        )
    } else {
        // NONE selected - show info
        Text(
            text = "Post-processing disabled. Only verbatim transcription will be available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
    }
}

