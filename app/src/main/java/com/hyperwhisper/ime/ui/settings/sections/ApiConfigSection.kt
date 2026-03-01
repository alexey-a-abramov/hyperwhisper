package com.hyperwhisper.ui.settings.sections

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
import androidx.compose.material.icons.filled.Info
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
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.settings.components.selectors.CloudProviderSelector
import com.hyperwhisper.ui.settings.components.selectors.LanguageSelector
import com.hyperwhisper.ui.settings.components.selectors.ModelSelector
import kotlinx.coroutines.delay

@Composable
fun ApiConfigSection(
    provider: ApiProvider,
    baseUrl: String,
    apiKey: String,
    requiresAuth: Boolean,
    modelId: String,
    inputLanguage: String,
    outputLanguage: String,
    connectionTestState: com.hyperwhisper.ui.settings.ConnectionTestState,
    llmApiKey: String = "", // For conditional "reuse" button
    onProviderChange: (ApiProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRequiresAuthChange: (Boolean) -> Unit,
    onModelIdChange: (String) -> Unit,
    onInputLanguageChange: (String) -> Unit,
    onOutputLanguageChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onResetDefaults: () -> Unit,
    onShowModelInfo: () -> Unit,
    onShowProviderKeyHelp: () -> Unit,
    onReuseProviderKeyForLlm: () -> Unit,
    onShowInputLanguageInfo: () -> Unit,
    onShowLogsDialog: () -> Unit,
    onShowApiCallLogs: () -> Unit,
    onResetConnectionTestState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Cloud provider selector
    CloudProviderSelector(
        selectedProvider = provider,
        onProviderSelected = onProviderChange,
        modifier = modifier.padding(bottom = 16.dp)
    )

    // Base URL with reset button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text(strings.baseUrl) },
            placeholder = { Text(provider.defaultEndpoint) },
            supportingText = { Text(strings.baseUrlHint) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(strings.reset.uppercase())
        }
    }

    Spacer(Modifier.padding(vertical = 8.dp))

    // No API Key checkbox - only for self-hosted Whisper
    if (provider == ApiProvider.SELFHOSTED_WHISPER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = !requiresAuth,
                onCheckedChange = { onRequiresAuthChange(!it) }
            )
            Text(
                text = "No API key required",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.padding(vertical = 8.dp))
    }

    // API Key (only shown if auth is required)
    if (requiresAuth) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(strings.apiKey) },
            placeholder = { Text(strings.apiKeyPlaceholder) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.padding(vertical = 4.dp))
        OutlinedButton(
            onClick = onShowProviderKeyHelp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("How to get API key")
        }

        // Only show reuse button if this key is set and LLM key is empty
        if (apiKey.isNotBlank() && llmApiKey.isBlank()) {
            Spacer(Modifier.padding(vertical = 4.dp))
            OutlinedButton(
                onClick = onReuseProviderKeyForLlm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reuse this key for post-processing")
            }
        }

        Spacer(Modifier.padding(vertical = 8.dp))

        // View API Call Logs button
        OutlinedButton(
            onClick = onShowApiCallLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View API Call Logs")
        }

        Spacer(Modifier.padding(vertical = 8.dp))
    }


    // Model selector with info button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelSelector(
            provider = provider,
            selectedModel = modelId,
            availableModels = provider.defaultModels,
            onModelSelected = onModelIdChange,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onShowModelInfo) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Model Info",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(Modifier.padding(vertical = 8.dp))

    // Input language with info button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageSelector(
            selectedLanguage = inputLanguage,
            onLanguageSelected = onInputLanguageChange,
            label = "Input Language (Speech)",
            supportingText = "Hint for speech recognition. Leave as Auto-detect if unsure.",
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onShowInputLanguageInfo) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Input Language Info",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(Modifier.padding(vertical = 8.dp))

    // Output language
    LanguageSelector(
        selectedLanguage = outputLanguage,
        onLanguageSelected = onOutputLanguageChange,
        label = "Output Language (Text)",
        supportingText = "Force output translation. Leave empty to keep original language."
    )

    Spacer(Modifier.padding(vertical = 8.dp))

    // Test connection and view logs buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier.weight(1f),
            enabled = (!requiresAuth || apiKey.isNotBlank()) && baseUrl.isNotBlank()
        ) {
            Text(strings.testConnection)
        }

        OutlinedButton(
            onClick = onShowLogsDialog,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "View Logs",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.viewApiLogs)
        }
    }

    Spacer(Modifier.padding(vertical = 8.dp))

    // Connection test result
    when (val state = connectionTestState) {
        is com.hyperwhisper.ui.settings.ConnectionTestState.Testing -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(strings.testingConnection)
                }
            }
        }
        is com.hyperwhisper.ui.settings.ConnectionTestState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            LaunchedEffect(Unit) {
                delay(3000)
                onResetConnectionTestState()
            }
        }
        is com.hyperwhisper.ui.settings.ConnectionTestState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            LaunchedEffect(Unit) {
                delay(5000)
                onResetConnectionTestState()
            }
        }
        else -> {}
    }
}
