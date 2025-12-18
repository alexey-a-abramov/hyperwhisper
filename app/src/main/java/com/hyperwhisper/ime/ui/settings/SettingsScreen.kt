package com.hyperwhisper.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.VoiceMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val apiSettings by viewModel.apiSettings.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()

    var provider by remember { mutableStateOf(apiSettings.provider) }
    var baseUrl by remember { mutableStateOf(apiSettings.baseUrl) }
    var apiKey by remember { mutableStateOf(apiSettings.apiKey) }
    var modelId by remember { mutableStateOf(apiSettings.modelId) }
    var language by remember { mutableStateOf(apiSettings.language) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }

    var showAddModeDialog by remember { mutableStateOf(false) }

    val connectionTestState by viewModel.connectionTestState.collectAsState()

    // Update fields when settings change
    LaunchedEffect(apiSettings) {
        provider = apiSettings.provider
        baseUrl = apiSettings.baseUrl
        apiKey = apiSettings.apiKey
        modelId = apiSettings.modelId
        language = apiSettings.language
    }

    // Auto-update base URL when provider changes
    LaunchedEffect(provider) {
        if (baseUrl.isEmpty() || baseUrl == apiSettings.provider.defaultEndpoint) {
            baseUrl = provider.defaultEndpoint
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HyperWhisper Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Settings Section
            item {
                Text(
                    text = "API Configuration",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                ProviderSelector(
                    selectedProvider = provider,
                    onProviderSelected = { provider = it }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text(provider.defaultEndpoint) },
                        supportingText = { Text("Must end with /") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            baseUrl = provider.defaultEndpoint
                            modelId = provider.defaultModels.firstOrNull() ?: modelId
                            viewModel.resetToDefaults(provider)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("RESET")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModelSelector(
                        selectedModel = modelId,
                        availableModels = provider.defaultModels,
                        onModelSelected = { modelId = it },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { showModelInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Model Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language (optional)") },
                    placeholder = { Text("en, es, ru, etc.") },
                    supportingText = { Text("ISO-639-1 code for Whisper. Leave empty for auto-detect") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveApiSettings(provider, baseUrl, apiKey, modelId, language)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Settings")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.testConnection(baseUrl, apiKey, modelId)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = apiKey.isNotBlank() && baseUrl.isNotBlank()
                    ) {
                        Text("Test Connection")
                    }
                }
            }

            // Connection test result
            item {
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
                                Text("Testing connection...")
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
                            kotlinx.coroutines.delay(3000)
                            viewModel.resetConnectionTestState()
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
                            kotlinx.coroutines.delay(5000)
                            viewModel.resetConnectionTestState()
                        }
                    }
                    else -> {}
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Voice Modes Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Modes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showAddModeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(voiceModes) { mode ->
                ModeCard(
                    mode = mode,
                    onDelete = { viewModel.deleteVoiceMode(mode.id) }
                )
            }
        }
    }

    if (showModelInfo) {
        ModelInfoDialog(
            provider = provider,
            modelId = modelId,
            onDismiss = { showModelInfo = false }
        )
    }

    if (showAddModeDialog) {
        AddModeDialog(
            onDismiss = { showAddModeDialog = false },
            onAdd = { name, prompt ->
                viewModel.addVoiceMode(name, prompt)
                showAddModeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelector(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("API Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ApiProvider.values().forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf(selectedModel) }
    val isCustomModel = !availableModels.contains(selectedModel)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {
                customModel = it
                onModelSelected(it)
            },
            label = { Text("Model ID") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
            supportingText = {
                if (isCustomModel && selectedModel.isNotEmpty()) {
                    Text("Custom model")
                }
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelSelected(model)
                        customModel = model
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ModeCard(
    mode: VoiceMode,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mode.systemPrompt,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (mode.isBuiltIn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Built-in",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!mode.isBuiltIn) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mode",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddModeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Voice Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mode Name") },
                    placeholder = { Text("e.g., Formal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    placeholder = { Text("e.g., Transcribe and make it formal") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onAdd(name, systemPrompt)
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ModelInfoDialog(
    provider: ApiProvider,
    modelId: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Model Information")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider name
                Text(
                    text = "Provider: ${provider.displayName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Divider()

                // Current model
                Text(
                    text = "Selected Model:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = modelId,
                    fontSize = 15.sp
                )

                Divider()

                // Provider-specific information
                when (provider) {
                    ApiProvider.OPENAI -> {
                        Text("OpenAI Whisper", fontWeight = FontWeight.Medium)
                        Text("• Multi-language support (98+ languages)", fontSize = 14.sp)
                        Text("• Translation to English", fontSize = 14.sp)
                        Text("• Word-level timestamps", fontSize = 14.sp)
                        Text("• Max file size: 25 MB", fontSize = 14.sp)
                        Text("• Supports: mp3, mp4, m4a, wav, webm", fontSize = 14.sp)
                    }
                    ApiProvider.DEEPGRAM -> {
                        Text("Deepgram Nova", fontWeight = FontWeight.Medium)
                        Text("• Extremely low latency", fontSize = 14.sp)
                        Text("• Speaker diarization", fontSize = 14.sp)
                        Text("• Smart formatting (punctuation)", fontSize = 14.sp)
                        Text("• Topic detection", fontSize = 14.sp)
                        Text("• Real-time & Batch processing", fontSize = 14.sp)
                    }
                    ApiProvider.ASSEMBLYAI -> {
                        Text("AssemblyAI Universal", fontWeight = FontWeight.Medium)
                        Text("• Speaker diarization", fontSize = 14.sp)
                        Text("• Audio intelligence features", fontSize = 14.sp)
                        Text("• PII Redaction", fontSize = 14.sp)
                        Text("• Auto-language detection", fontSize = 14.sp)
                        Text("• Async workflow", fontSize = 14.sp)
                    }
                    ApiProvider.GOOGLE_CLOUD -> {
                        Text("Google Cloud Speech", fontWeight = FontWeight.Medium)
                        Text("• Chirp Universal Speech Model", fontSize = 14.sp)
                        Text("• Domain-specific models", fontSize = 14.sp)
                        Text("• Profanity filtering", fontSize = 14.sp)
                        Text("• Automatic punctuation", fontSize = 14.sp)
                        Text("• Noise robustness", fontSize = 14.sp)
                    }
                    ApiProvider.AWS_TRANSCRIBE -> {
                        Text("AWS Transcribe", fontWeight = FontWeight.Medium)
                        Text("• Custom vocabulary", fontSize = 14.sp)
                        Text("• Vocabulary filtering", fontSize = 14.sp)
                        Text("• Speaker identification", fontSize = 14.sp)
                        Text("• Channel identification", fontSize = 14.sp)
                        Text("• Medical & Standard models", fontSize = 14.sp)
                    }
                    ApiProvider.AZURE_SPEECH -> {
                        Text("Azure AI Speech", fontWeight = FontWeight.Medium)
                        Text("• Custom Speech training", fontSize = 14.sp)
                        Text("• Pronunciation assessment", fontSize = 14.sp)
                        Text("• Phrase lists (dynamic grammar)", fontSize = 14.sp)
                        Text("• Silent pause support", fontSize = 14.sp)
                        Text("• Fast/Batch modes", fontSize = 14.sp)
                    }
                    ApiProvider.REVAI -> {
                        Text("Rev.ai", fontWeight = FontWeight.Medium)
                        Text("• High accuracy on accents", fontSize = 14.sp)
                        Text("• Human transcription fallback", fontSize = 14.sp)
                        Text("• Speaker identification", fontSize = 14.sp)
                        Text("• Custom vocabularies", fontSize = 14.sp)
                        Text("• Async workflow", fontSize = 14.sp)
                    }
                    ApiProvider.GROQ -> {
                        Text("Groq Whisper", fontWeight = FontWeight.Medium)
                        Text("• Ultra-fast inference", fontSize = 14.sp)
                        Text("• Whisper large-v3 models", fontSize = 14.sp)
                        Text("• Distil-whisper (faster)", fontSize = 14.sp)
                        Text("• Multi-language support", fontSize = 14.sp)
                        Text("• OpenAI-compatible API", fontSize = 14.sp)
                    }
                    ApiProvider.OPENROUTER -> {
                        Text("OpenRouter", fontWeight = FontWeight.Medium)
                        Text("• Access to multiple models", fontSize = 14.sp)
                        Text("• Unified API interface", fontSize = 14.sp)
                        Text("• Pay-per-use pricing", fontSize = 14.sp)
                        Text("• No subscriptions", fontSize = 14.sp)
                    }
                    ApiProvider.GEMINI -> {
                        Text("Google Gemini", fontWeight = FontWeight.Medium)
                        Text("• Multimodal AI model", fontSize = 14.sp)
                        Text("• Audio + text processing", fontSize = 14.sp)
                        Text("• Context understanding", fontSize = 14.sp)
                        Text("• Latest 2.0 Flash model", fontSize = 14.sp)
                    }
                    ApiProvider.HUGGINGFACE -> {
                        Text("Hugging Face", fontWeight = FontWeight.Medium)
                        Text("• Open source models", fontSize = 14.sp)
                        Text("• Whisper variants", fontSize = 14.sp)
                        Text("• Free inference API", fontSize = 14.sp)
                        Text("• Community-driven", fontSize = 14.sp)
                    }
                }

                Divider()

                // Available models
                Text(
                    text = "Available Models:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                provider.defaultModels.forEach { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (model == modelId) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (model == modelId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = model,
                            fontSize = 14.sp,
                            fontWeight = if (model == modelId) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}
