package com.hyperwhisper.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.data.DarkModePreference
import com.hyperwhisper.data.FontFamilyOption
import com.hyperwhisper.data.LocalSettings
import com.hyperwhisper.data.ModelDownloadState
import com.hyperwhisper.data.UIScaleOption
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.WhisperModel
import com.hyperwhisper.data.SUPPORTED_LANGUAGES
import com.hyperwhisper.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    isLocalFlavorEnabled: Boolean = true  // Default to local flavor, cloud builds will pass false
) {
    val apiSettings by viewModel.apiSettings.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()
    val appearanceSettings by viewModel.appearanceSettings.collectAsState()
    val modelStates by viewModel.modelStates.collectAsState()

    var provider by remember { mutableStateOf(apiSettings.provider) }
    var baseUrl by remember { mutableStateOf(apiSettings.baseUrl) }
    var apiKey by remember { mutableStateOf(apiSettings.getCurrentApiKey()) }
    var modelId by remember { mutableStateOf(apiSettings.modelId) }
    var inputLanguage by remember { mutableStateOf(apiSettings.inputLanguage) }
    var outputLanguage by remember { mutableStateOf(apiSettings.outputLanguage) }
    var localSettings by remember { mutableStateOf(apiSettings.localSettings) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }
    var showInputLanguageInfo by remember { mutableStateOf(false) }

    var showAddModeDialog by remember { mutableStateOf(false) }
    var editingMode by remember { mutableStateOf<VoiceMode?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }

    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val context = LocalContext.current
    val strings = LocalStrings.current

    // Update fields when settings change
    LaunchedEffect(apiSettings) {
        provider = apiSettings.provider
        baseUrl = apiSettings.baseUrl
        apiKey = apiSettings.getCurrentApiKey()
        modelId = apiSettings.modelId
        inputLanguage = apiSettings.inputLanguage
        outputLanguage = apiSettings.outputLanguage
        localSettings = apiSettings.localSettings
    }

    // Update API key and defaults when provider changes
    LaunchedEffect(provider) {
        apiKey = apiSettings.apiKeys[provider] ?: ""
        if (baseUrl.isEmpty() || baseUrl == apiSettings.provider.defaultEndpoint) {
            baseUrl = provider.defaultEndpoint
        }
        // Auto-select first model for provider
        if (modelId.isEmpty() || !provider.defaultModels.contains(modelId)) {
            modelId = provider.defaultModels.firstOrNull() ?: modelId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                actions = {
                    IconButton(onClick = {
                        val intent = android.content.Intent(context, com.hyperwhisper.ui.about.AboutActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onPrimary
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
                ProcessingModeToggle(
                    isLocalMode = provider == ApiProvider.LOCAL,
                    onModeChanged = { isLocal ->
                        provider = if (isLocal) ApiProvider.LOCAL else ApiProvider.OPENAI
                    },
                    isLocalFlavorEnabled = isLocalFlavorEnabled,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // LOCAL provider configuration
            if (provider == ApiProvider.LOCAL && isLocalFlavorEnabled) {
                item {
                    LocalModelSelector(
                        selectedModel = localSettings.selectedModel,
                        modelStates = modelStates,
                        onModelSelected = { model ->
                            localSettings = localSettings.copy(selectedModel = model)
                        }
                    )
                }

                item {
                    LocalPrerequisitesCard(
                        selectedModel = localSettings.selectedModel,
                        modelState = modelStates[localSettings.selectedModel] ?: ModelDownloadState.NotDownloaded,
                        onDownloadModel = {
                            viewModel.downloadModel(localSettings.selectedModel)
                        }
                    )
                }

                item {
                    ProminentHybridProcessingCard(
                        localSettings = localSettings,
                        onLocalSettingsChanged = { newSettings ->
                            localSettings = newSettings
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            // Cloud provider configuration
            if (provider != ApiProvider.LOCAL) {
                item {
                    CloudProviderSelector(
                        selectedProvider = provider,
                        onProviderSelected = { newProvider ->
                            provider = newProvider
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
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
                        label = { Text(strings.baseUrl) },
                        placeholder = { Text(provider.defaultEndpoint) },
                        supportingText = { Text(strings.baseUrlHint) },
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
                        Text(strings.reset.uppercase())
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(strings.apiKey) },
                    placeholder = { Text(strings.apiKeyPlaceholder) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageSelector(
                        selectedLanguage = inputLanguage,
                        onLanguageSelected = { inputLanguage = it },
                        label = "Input Language (Speech)",
                        supportingText = "Hint for speech recognition. Leave as Auto-detect if unsure.",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showInputLanguageInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Input Language Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                LanguageSelector(
                    selectedLanguage = outputLanguage,
                    onLanguageSelected = { outputLanguage = it },
                    label = "Output Language (Text)",
                    supportingText = "Force output translation. Leave empty to keep original language."
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.testConnection(baseUrl, apiKey, modelId)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = apiKey.isNotBlank() && baseUrl.isNotBlank()
                    ) {
                        Text(strings.testConnection)
                    }

                    OutlinedButton(
                        onClick = { showLogsDialog = true },
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
            }  // End of cloud provider configuration

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Appearance Section
            item {
                SectionCard(
                    title = strings.appearanceSettings,
                    icon = Icons.Default.Palette
                ) {
                    AppearanceSection(
                        appearanceSettings = appearanceSettings,
                        onSettingsChange = { newSettings ->
                            viewModel.saveAppearanceSettings(newSettings)
                        }
                    )
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Local Models Section
            item {
                Text(
                    text = "Local Models",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Show model management only for local flavor
            if (isLocalFlavorEnabled) {
                item {
                    ModelManagementCard()
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
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
                val isVerbatim = mode.id == "verbatim"
                val isLocalProvider = provider == ApiProvider.LOCAL
                val secondStageEnabled = localSettings.enableSecondStageProcessing

                val isEnabled = when {
                    !isLocalProvider -> true
                    isVerbatim -> true
                    secondStageEnabled -> true
                    else -> false
                }

                val disabledReason = if (!isEnabled) {
                    "Enable 'Cloud Processing' in the API Configuration section above to use transformation modes with local transcription"
                } else null

                ModeCardWithTooltip(
                    mode = mode,
                    isEnabled = isEnabled,
                    disabledReason = disabledReason,
                    onEdit = { if (isEnabled) editingMode = mode },
                    onDelete = { if (isEnabled) viewModel.deleteVoiceMode(mode.id) }
                )
            }

            item { // Moved Save Settings button to the bottom
                Button(
                    onClick = {
                        viewModel.saveApiSettings(provider, baseUrl, apiKey, modelId, inputLanguage, outputLanguage, localSettings)
                        (context as? android.app.Activity)?.finish() // Close settings after saving
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.saveAndCloseSettings)
                }
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

    editingMode?.let { mode ->
        EditModeDialog(
            mode = mode,
            onDismiss = { editingMode = null },
            onUpdate = { updatedMode ->
                viewModel.updateVoiceMode(updatedMode)
                editingMode = null
            }
        )
    }

    if (showInputLanguageInfo) {
        InputLanguageInfoDialog(
            onDismiss = { showInputLanguageInfo = false }
        )
    }

    if (showLogsDialog) {
        LogsInfoDialog(
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
fun InputLanguageInfoDialog(onDismiss: () -> Unit) {
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
                Text("Input Language Hint")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This setting provides a hint to the speech recognition model about the language being spoken. While 'Auto-detect' works well in most cases, providing a specific language can improve accuracy:",
                    fontSize = 14.sp
                )
                Text("• For speakers with strong accents.", fontSize = 14.sp)
                Text("• For less common languages or dialects.", fontSize = 14.sp)
                Text("• In noisy environments.", fontSize = 14.sp)
                Divider()
                Text(
                    "If your transcriptions are inaccurate, try setting this to your native language.",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}

@Composable
fun LogsInfoDialog(onDismiss: () -> Unit) {
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
                Text("View API Logs")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "HyperWhisper logs all API requests and responses for debugging.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Divider()

                Text(
                    "Viewing Logs:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text("• Use ADB: adb logcat | grep HyperWhisper", fontSize = 13.sp)
                Text("• Install a logcat app from Play Store", fontSize = 13.sp)
                Text("• Filter by: ChatCompletionStrategy, VoiceRepository", fontSize = 13.sp)

                Divider()

                Text(
                    "Logged Information:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text("• API request details (URL, model, prompts)", fontSize = 13.sp)
                Text("• Response status and content", fontSize = 13.sp)
                Text("• Token usage (input/output/total)", fontSize = 13.sp)
                Text("• Audio file information", fontSize = 13.sp)
                Text("• Error messages and traces", fontSize = 13.sp)

                Divider()

                Text(
                    "Note: Logs show first 10 chars of API keys only.",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelector(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit,
    isLocalFlavorEnabled: Boolean = true,  // Default to local flavor
    showLocalOption: Boolean = true  // NEW parameter to explicitly hide LOCAL
) {
    var expanded by remember { mutableStateOf(false) }

    // Filter available providers
    val availableProviders = remember(isLocalFlavorEnabled, showLocalOption) {
        ApiProvider.values().filter { provider ->
            if (!isLocalFlavorEnabled && provider == ApiProvider.LOCAL) {
                false  // Hide LOCAL if flavor not enabled
            } else if (!showLocalOption && provider == ApiProvider.LOCAL) {
                false  // Hide LOCAL if explicitly disabled
            } else {
                true
            }
        }
    }

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
            availableProviders.forEach { provider ->
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

@Composable
fun ProcessingModeToggle(
    isLocalMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    isLocalFlavorEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Transcription Source",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Segmented button toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Cloud option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (!isLocalMode) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onModeChanged(false) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (!isLocalMode) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cloud API",
                        color = if (!isLocalMode) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = if (!isLocalMode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Local option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isLocalMode) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable(enabled = isLocalFlavorEnabled) {
                        onModeChanged(true)
                    }
                    .alpha(if (isLocalFlavorEnabled) 1f else 0.5f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (isLocalMode) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local",
                        color = if (isLocalMode) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = if (isLocalMode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Hint text below toggle
        Text(
            text = if (!isLocalMode) "Use cloud API services for transcription"
                   else "On-device processing with whisper.cpp (privacy-first)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )

        // Warning if local not available
        if (!isLocalFlavorEnabled && isLocalMode) {
            Text(
                text = "⚠️ Local processing not available in this build variant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProviderSelector(
    selectedProvider: ApiProvider,
    onProviderSelected: (ApiProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Filter to cloud providers only (exclude LOCAL)
    val cloudProviders = remember {
        ApiProvider.values().filter { it != ApiProvider.LOCAL }
    }

    Column(modifier = modifier) {
        Text(
            text = "Cloud Provider",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedProvider.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                cloudProviders.forEach { provider ->
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
}

@Composable
fun ModeCard(
    mode: VoiceMode,
    onEdit: () -> Unit,
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
fun EditModeDialog(
    mode: VoiceMode,
    onDismiss: () -> Unit,
    onUpdate: (VoiceMode) -> Unit
) {
    var name by remember { mutableStateOf(mode.name) }
    var systemPrompt by remember { mutableStateOf(mode.systemPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Voice Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mode Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    placeholder = { Text("Enter the system prompt for this mode") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onUpdate(mode.copy(name = name, systemPrompt = systemPrompt))
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("Save")
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
    onDismiss: () -> Unit,
    isLocalFlavorEnabled: Boolean = true  // Default to local flavor
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
                    ApiProvider.LOCAL -> {
                        Text("Local (whisper.cpp)", fontWeight = FontWeight.Medium)
                        Text("• 100% offline - no internet required", fontSize = 14.sp)
                        Text("• Complete privacy - data never leaves device", fontSize = 14.sp)
                        Text("• No API costs", fontSize = 14.sp)
                        Text("• Multiple model sizes (tiny, base, small)", fontSize = 14.sp)
                        Text("• Fast inference on mobile devices", fontSize = 14.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedLang = SUPPORTED_LANGUAGES.find { it.code == selectedLanguage }

    // Fuzzy search filter
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            SUPPORTED_LANGUAGES
        } else {
            val query = searchQuery.lowercase()
            SUPPORTED_LANGUAGES.filter { language ->
                language.name.lowercase().contains(query) ||
                language.code.lowercase().contains(query) ||
                // Fuzzy match: check if query letters appear in order
                language.name.lowercase().let { name ->
                    var queryIndex = 0
                    name.forEach { char ->
                        if (queryIndex < query.length && char == query[queryIndex]) {
                            queryIndex++
                        }
                    }
                    queryIndex == query.length
                }
            }.sortedBy { language ->
                // Prioritize exact matches and starts-with matches
                when {
                    language.name.lowercase() == query -> 0
                    language.code.lowercase() == query -> 1
                    language.name.lowercase().startsWith(query) -> 2
                    language.code.lowercase().startsWith(query) -> 3
                    language.name.lowercase().contains(query) -> 4
                    else -> 5
                }
            }
        }
    }

    // Reset search when menu closes
    LaunchedEffect(expanded) {
        if (!expanded) {
            searchQuery = ""
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLang?.name ?: "Auto-detect",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = { Text(supportingText, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search languages...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Divider()

            // Filtered language list
            filteredLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(language.name)
                            if (language.code.isNotEmpty()) {
                                Text(
                                    text = language.code,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                        searchQuery = ""
                    }
                )
            }

            if (filteredLanguages.isEmpty()) {
                Text(
                    text = "No languages found",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Section content card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    appearanceSettings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit
) {
    var localSettings by remember { mutableStateOf(appearanceSettings) }

    // Update when settings change externally
    LaunchedEffect(appearanceSettings) {
        localSettings = appearanceSettings
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Color Scheme Selector
        Text(
            text = "Color Scheme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        ColorSchemeSelector(
            selectedScheme = localSettings.colorScheme,
            onSchemeSelected = { scheme ->
                val newSettings = localSettings.copy(colorScheme = scheme)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        // Dynamic Color Toggle (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Dynamic Color",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Match system wallpaper colors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = localSettings.useDynamicColor,
                    onCheckedChange = { enabled ->
                        val newSettings = localSettings.copy(useDynamicColor = enabled)
                        localSettings = newSettings
                        onSettingsChange(newSettings)
                    }
                )
            }
        }

        Divider()

        // Dark Mode Preference Selector
        Text(
            text = "Theme Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        DarkModeSelector(
            selectedMode = localSettings.darkModePreference,
            onModeSelected = { mode ->
                val newSettings = localSettings.copy(darkModePreference = mode)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // UI Scale Selector
        Text(
            text = "Text Size",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        UIScaleSelector(
            selectedScale = localSettings.uiScale,
            onScaleSelected = { scale ->
                val newSettings = localSettings.copy(uiScale = scale)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // Font Family Selector
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        FontFamilySelector(
            selectedFont = localSettings.fontFamily,
            onFontSelected = { font ->
                val newSettings = localSettings.copy(fontFamily = font)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // UI Language Selector
        Text(
            text = "Interface Language",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        UILanguageSelector(
            selectedLanguageCode = localSettings.uiLanguage,
            onLanguageSelected = { languageCode ->
                val newSettings = localSettings.copy(uiLanguage = languageCode)
                localSettings = newSettings
                onSettingsChange(newSettings)
            }
        )

        Divider()

        // Feature Toggles
        Text(
            text = "Features",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        // Auto-copy to clipboard toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-copy to Clipboard",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Automatically copy transcribed text to clipboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.autoCopyToClipboard,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(autoCopyToClipboard = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History panel toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable History Panel",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Long press paste button to view last 20 transcriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = localSettings.enableHistoryPanel,
                onCheckedChange = { enabled ->
                    val newSettings = localSettings.copy(enableHistoryPanel = enabled)
                    localSettings = newSettings
                    onSettingsChange(newSettings)
                }
            )
        }
    }
}

@Composable
fun ColorSchemeSelector(
    selectedScheme: ColorSchemeOption,
    onSchemeSelected: (ColorSchemeOption) -> Unit
) {
    // Grid layout with 2 columns for better fit
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorSchemeOption.values().toList().chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowThemes.forEach { option ->
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSchemeSelected(option) }
                            .background(
                                color = if (option == selectedScheme)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        // Three color circles in a row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = option.primaryColor,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            )
                            // Secondary color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = option.secondaryColor,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            )
                            // Tertiary color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = option.tertiaryColor,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            )

                            // Check mark if selected
                            if (option == selectedScheme) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Theme name
                        Text(
                            text = option.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (option == selectedScheme) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                // Fill empty space if odd number of themes
                if (rowThemes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun UIScaleSelector(
    selectedScale: UIScaleOption,
    onScaleSelected: (UIScaleOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UIScaleOption.values().forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selectedScale,
                        onClick = { onScaleSelected(option) }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = option.displayName,
                    fontSize = (16.sp.value * option.scale).sp,
                    fontWeight = if (option == selectedScale) FontWeight.Bold else FontWeight.Normal
                )
                RadioButton(
                    selected = option == selectedScale,
                    onClick = { onScaleSelected(option) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontFamilySelector(
    selectedFont: FontFamilyOption,
    onFontSelected: (FontFamilyOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedFont.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Font") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FontFamilyOption.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.displayName,
                            fontFamily = option.fontFamily
                        )
                    },
                    onClick = {
                        onFontSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeSelector(
    selectedMode: DarkModePreference,
    onModeSelected: (DarkModePreference) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Dark Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DarkModePreference.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onModeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UILanguageSelector(
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguage = com.hyperwhisper.localization.getLanguageByCode(selectedLanguageCode)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLanguage.nativeName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            com.hyperwhisper.localization.AppLanguage.values().forEach { language ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = language.nativeName,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = language.displayName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Local model selector for choosing between Tiny/Base/Small models
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelSelector(
    selectedModel: WhisperModel,
    modelStates: Map<WhisperModel, ModelDownloadState>,
    onModelSelected: (WhisperModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Local Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            leadingIcon = {
                val state = modelStates[selectedModel] ?: ModelDownloadState.NotDownloaded
                when (state) {
                    is ModelDownloadState.Downloaded -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    else -> Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not downloaded",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            supportingText = {
                Text(selectedModel.getFormattedSize())
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WhisperModel.values().forEach { model ->
                val state = modelStates[model] ?: ModelDownloadState.NotDownloaded
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName)
                                Text(
                                    text = model.getFormattedSize(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            when (state) {
                                is ModelDownloadState.Downloaded -> Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                else -> Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Not downloaded",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Card showing second-stage cloud processing option for LOCAL provider
 */
@Composable
fun SecondStageProcessingCard(
    localSettings: LocalSettings,
    onLocalSettingsChanged: (LocalSettings) -> Unit,
    availableProviders: List<ApiProvider>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title and switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud Processing (Optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enable cloud API for transformations and translations",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = localSettings.enableSecondStageProcessing,
                    onCheckedChange = { enabled ->
                        onLocalSettingsChanged(localSettings.copy(enableSecondStageProcessing = enabled))
                    }
                )
            }

            // Explanation text
            if (!localSettings.enableSecondStageProcessing) {
                Text(
                    text = "Only VERBATIM mode available. Enable cloud processing to use transformations.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }

            // Cloud provider selector (shown when enabled)
            if (localSettings.enableSecondStageProcessing) {
                Divider()

                Text(
                    text = "Hybrid Workflow: Local transcription → Cloud transformation",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )

                ProviderSelector(
                    selectedProvider = localSettings.secondStageProvider,
                    onProviderSelected = { provider ->
                        onLocalSettingsChanged(localSettings.copy(secondStageProvider = provider))
                    }
                )

                ModelSelector(
                    selectedModel = localSettings.secondStageModel,
                    availableModels = localSettings.secondStageProvider.defaultModels,
                    onModelSelected = { model ->
                        onLocalSettingsChanged(localSettings.copy(secondStageModel = model))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Cost warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Cloud processing will use API credits. Costs apply based on usage.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ProminentHybridProcessingCard(
    localSettings: LocalSettings,
    onLocalSettingsChanged: (LocalSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (localSettings.enableSecondStageProcessing)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflineBolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Optional: Cloud Processing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enable transformations & translations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Switch(
                    checked = localSettings.enableSecondStageProcessing,
                    onCheckedChange = { enabled ->
                        onLocalSettingsChanged(
                            localSettings.copy(enableSecondStageProcessing = enabled)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation
            if (!localSettings.enableSecondStageProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Only VERBATIM mode available. Enable cloud processing to use transformation modes (Polite, Casual, etc.) and translations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Show cloud provider/model selection when enabled
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Hybrid Workflow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Local transcription → Cloud transformation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Provider selector (but without LOCAL option)
                    ProviderSelector(
                        selectedProvider = localSettings.secondStageProvider,
                        onProviderSelected = { provider ->
                            onLocalSettingsChanged(
                                localSettings.copy(secondStageProvider = provider)
                            )
                        },
                        isLocalFlavorEnabled = false,  // Hide LOCAL from this dropdown
                        showLocalOption = false        // Extra safety
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ModelSelector(
                        selectedModel = localSettings.secondStageModel,
                        availableModels = localSettings.secondStageProvider.defaultModels,
                        onModelSelected = { model ->
                            onLocalSettingsChanged(
                                localSettings.copy(secondStageModel = model)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Cost warning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cloud API will be used for post-processing (may incur costs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Prerequisites status card for LOCAL provider
 */
@Composable
fun LocalPrerequisitesCard(
    selectedModel: WhisperModel,
    modelState: ModelDownloadState,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = modelState is ModelDownloadState.Downloaded

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isReady) "Ready to Use" else "Download Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${selectedModel.displayName} (${selectedModel.getFormattedSize()})",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Show progress for downloading state
                if (modelState is ModelDownloadState.Downloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = modelState.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(modelState.progress * 100).toInt()}% complete",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Show error message
                if (modelState is ModelDownloadState.Error) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = modelState.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Download button (shown when not ready)
            if (!isReady && modelState !is ModelDownloadState.Downloading) {
                Button(
                    onClick = onDownloadModel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download")
                }
            }
        }
    }
}

/**
 * Enhanced ModeCard with tooltip for disabled states
 */
@Composable
fun ModeCardWithTooltip(
    mode: VoiceMode,
    isEnabled: Boolean,
    disabledReason: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTooltip by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.5f)
            .clickable(enabled = !isEnabled) { showTooltip = true },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Lock icon for disabled modes
                if (!isEnabled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Disabled",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = mode.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mode.systemPrompt,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Mode",
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mode",
                        tint = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }

    // Tooltip dialog
    if (showTooltip && disabledReason != null) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Mode Disabled") },
            text = { Text(disabledReason) },
            confirmButton = {
                TextButton(onClick = { showTooltip = false }) {
                    Text("OK")
                }
            }
        )
    }
}
