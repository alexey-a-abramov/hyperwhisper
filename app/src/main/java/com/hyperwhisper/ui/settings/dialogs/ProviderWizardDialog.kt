package com.hyperwhisper.ui.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ProviderConfig
import com.hyperwhisper.ui.settings.components.selectors.CloudProviderSelector
import com.hyperwhisper.ui.settings.components.selectors.LanguageSelector
import com.hyperwhisper.ui.settings.components.selectors.ModelSelector

@Composable
fun ProviderWizardDialog(
    initialProvider: ApiProvider,
    initialBaseUrl: String,
    initialApiKey: String,
    initialRequiresAuth: Boolean,
    initialModelId: String,
    initialInputLanguage: String,
    initialOutputLanguage: String,
    onDismiss: () -> Unit,
    onSave: (ApiProvider, String, String, Boolean, String, String, String) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    // Wizard State
    var provider by remember { mutableStateOf(initialProvider) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var requiresAuth by remember { mutableStateOf(initialRequiresAuth) }
    var modelId by remember { mutableStateOf(initialModelId) }
    var inputLanguage by remember { mutableStateOf(initialInputLanguage) }
    var outputLanguage by remember { mutableStateOf(initialOutputLanguage) }

    var showKeyHelp by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Provider Setup",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Step $step of 3: ${getStepTitle(step)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        1 -> StepProviderSelection(
                            selectedProvider = provider,
                            onProviderSelected = { 
                                provider = it 
                                baseUrl = it.defaultEndpoint
                                requiresAuth = it.requiresAuth
                                modelId = it.defaultModels.firstOrNull() ?: ""
                            }
                        )
                        2 -> StepConnectionSettings(
                            provider = provider,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            requiresAuth = requiresAuth,
                            onBaseUrlChange = { baseUrl = it },
                            onApiKeyChange = { apiKey = it },
                            onRequiresAuthChange = { requiresAuth = it },
                            onShowKeyHelp = { showKeyHelp = true },
                            onResetDefaults = {
                                baseUrl = provider.defaultEndpoint
                                requiresAuth = provider.requiresAuth
                            }
                        )
                        3 -> StepModelLanguageSettings(
                            provider = provider,
                            modelId = modelId,
                            inputLanguage = inputLanguage,
                            outputLanguage = outputLanguage,
                            onModelIdChange = { modelId = it },
                            onInputLanguageChange = { inputLanguage = it },
                            onOutputLanguageChange = { outputLanguage = it }
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL")
                    }
                    
                    Row {
                        if (step > 1) {
                            OutlinedButton(
                                onClick = { step-- },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("BACK")
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (step < 3) step++
                                else {
                                    onSave(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
                                }
                            }
                        ) {
                            Text(if (step < 3) "NEXT" else "FINISH")
                        }
                    }
                }
            }
        }
    }

    if (showKeyHelp) {
        ProviderKeyInstructionsDialog(
            provider = provider,
            onDismiss = { showKeyHelp = false }
        )
    }
}

@Composable
private fun StepProviderSelection(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit
) {
    Text(
        "Choose an AI provider for transcription. Each provider has different pricing and model capabilities.",
        style = MaterialTheme.typography.bodyMedium
    )
    
    CloudProviderSelector(
        selectedProvider = selectedProvider,
        onProviderSelected = onProviderSelected
    )
    
    Spacer(Modifier.height(8.dp))
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Provider Info", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = getProviderDescription(selectedProvider),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StepConnectionSettings(
    provider: ApiProvider,
    baseUrl: String,
    apiKey: String,
    requiresAuth: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRequiresAuthChange: (Boolean) -> Unit,
    onShowKeyHelp: () -> Unit,
    onResetDefaults: () -> Unit
) {
    Text(
        "Configure how to connect to ${provider.displayName}. Most cloud providers require an API key.",
        style = MaterialTheme.typography.bodyMedium
    )

    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onResetDefaults) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    )

    if (provider == ApiProvider.SELFHOSTED_WHISPER || provider == ApiProvider.ANTIGRAVITY) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = !requiresAuth, onCheckedChange = { onRequiresAuthChange(!it) })
            Text("No authentication required")
        }
    }

    if (requiresAuth) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = onShowKeyHelp) {
                    Icon(Icons.Default.Help, contentDescription = "Help")
                }
            }
        )
    }
}

@Composable
private fun StepModelLanguageSettings(
    provider: ApiProvider,
    modelId: String,
    inputLanguage: String,
    outputLanguage: String,
    onModelIdChange: (String) -> Unit,
    onInputLanguageChange: (String) -> Unit,
    onOutputLanguageChange: (String) -> Unit
) {
    Text(
        "Select the specific model and language preferences for this provider.",
        style = MaterialTheme.typography.bodyMedium
    )

    ModelSelector(
        provider = provider,
        selectedModel = modelId,
        availableModels = provider.defaultModels,
        onModelSelected = onModelIdChange
    )

    LanguageSelector(
        selectedLanguage = inputLanguage,
        onLanguageSelected = onInputLanguageChange,
        label = "Input Language (Speech)",
        supportingText = "Auto-detect is recommended for multi-lingual models."
    )

    LanguageSelector(
        selectedLanguage = outputLanguage,
        onLanguageSelected = onOutputLanguageChange,
        label = "Output Language (Text)",
        supportingText = "Keep empty to get text in original language."
    )
}

private fun getStepTitle(step: Int): String = when (step) {
    1 -> "Select Provider"
    2 -> "Connection & Auth"
    3 -> "Model & Language"
    else -> ""
}

private fun getProviderDescription(provider: ApiProvider): String = when (provider) {
    ApiProvider.OPENAI -> "Industry standard Whisper models. Very reliable and accurate."
    ApiProvider.GROQ -> "Extremely fast inference using LPU technology. Great for real-time use."
    ApiProvider.DEEPGRAM -> "Enterprise-grade speech recognition with very low latency."
    ApiProvider.GEMINI -> "Google's latest multimodal models. Supports long audio and complex context."
    ApiProvider.LOCAL_WHISPER -> "Runs entirely on your device. 100% private, no internet needed."
    ApiProvider.SELFHOSTED_WHISPER -> "Connect to your own whisper.cpp or Faster-Whisper server."
    ApiProvider.MISTRAL -> "High-quality open-weights models from Mistral AI."
    ApiProvider.DEEPSEEK -> "Cost-effective and highly capable models from DeepSeek."
    else -> "Cloud-based speech recognition API."
}
