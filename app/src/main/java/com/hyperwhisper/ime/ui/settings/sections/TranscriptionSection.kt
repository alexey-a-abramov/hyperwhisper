package com.hyperwhisper.ui.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.LocalModelInfo
import com.hyperwhisper.data.LocalModelSettings
import com.hyperwhisper.data.LocalModelType
import com.hyperwhisper.data.WhisperDownloadState
import com.hyperwhisper.data.WhisperModelCatalog
import com.hyperwhisper.data.WhisperModelEntry
import com.hyperwhisper.ui.settings.ConnectionTestState
import com.hyperwhisper.ui.settings.OpenRouterModelInfo
import com.hyperwhisper.ui.settings.SettingsStatusLabels
import com.hyperwhisper.ui.settings.TestLogEntry
import com.hyperwhisper.ui.settings.TestLogLevel
import com.hyperwhisper.ui.settings.components.selectors.CloudProviderSelector
import com.hyperwhisper.ui.settings.components.selectors.LanguageSelector
import com.hyperwhisper.ui.settings.components.selectors.ModelSelector
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified Transcription detail screen.
 * Top-level pivot: Cloud ↔ On-device. Sub-panel renders accordingly.
 */
private enum class TranscriptionTab { CLOUD, LOCAL, TOOLS }

@Composable
fun TranscriptionSection(
    apiSettings: ApiSettings,
    discoveredModels: List<LocalModelInfo>,
    connectionTestState: ConnectionTestState,
    transcriptionTestLog: List<TestLogEntry> = emptyList(),
    whisperDownloadStates: Map<String, WhisperDownloadState> = emptyMap(),
    onSaveCloud: (
        provider: ApiProvider,
        baseUrl: String,
        apiKey: String,
        requiresAuth: Boolean,
        modelId: String,
        inputLanguage: String,
        outputLanguage: String
    ) -> Unit,
    onUpdateLocalSettings: (LocalModelSettings) -> Unit,
    onDiscoverModels: () -> Unit,
    onVerifyModel: (String) -> Unit,
    onTestConnection: () -> Unit,
    onResetConnectionState: () -> Unit,
    onShowProviderKeyHelp: () -> Unit,
    onShowApiCallLogs: () -> Unit,
    onSetActiveCloud: () -> Unit = {},
    onSetActiveLocalModel: (String) -> Unit = {},
    onStartWhisperDownload: (String) -> Unit = {},
    onCancelWhisperDownload: (String) -> Unit = {},
    onDeleteDownloadedWhisper: (String) -> Unit = {},
    openRouterModels: List<OpenRouterModelInfo> = emptyList(),
    openRouterRefreshing: Boolean = false,
    openRouterError: String? = null,
    onRefreshOpenRouterModels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val useLocal = apiSettings.localModelSettings.useLocalWhisper
    var selectedTab by remember {
        mutableStateOf(if (useLocal) TranscriptionTab.LOCAL else TranscriptionTab.CLOUD)
    }
    // Keep tab in sync if useLocal flips elsewhere (e.g. via a "Set active"
    // tap from Local Models or a voice command).
    LaunchedEffect(useLocal) {
        selectedTab = if (useLocal) TranscriptionTab.LOCAL else TranscriptionTab.CLOUD
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SourcePivot(
            selected = selectedTab,
            onSelect = { tab ->
                selectedTab = tab
                when (tab) {
                    TranscriptionTab.CLOUD -> if (useLocal) {
                        onUpdateLocalSettings(apiSettings.localModelSettings.copy(useLocalWhisper = false))
                    }
                    TranscriptionTab.LOCAL -> if (!useLocal) {
                        onUpdateLocalSettings(apiSettings.localModelSettings.copy(useLocalWhisper = true))
                    }
                    TranscriptionTab.TOOLS -> Unit // TODO: wire Tools panel
                }
            }
        )

        when (selectedTab) {
            TranscriptionTab.LOCAL -> LocalWhisperPanel(
                settings = apiSettings.localModelSettings,
                discoveredModels = discoveredModels.filter { it.type == LocalModelType.WHISPER },
                onUpdate = onUpdateLocalSettings,
                onDiscover = onDiscoverModels,
                onVerify = onVerifyModel,
                onSetActiveLocalModel = onSetActiveLocalModel
            )
            TranscriptionTab.CLOUD -> CloudPanel(
                apiSettings = apiSettings,
                onSave = onSaveCloud,
                onShowProviderKeyHelp = onShowProviderKeyHelp,
                onSetActive = onSetActiveCloud,
                openRouterModels = openRouterModels,
                openRouterRefreshing = openRouterRefreshing,
                openRouterError = openRouterError,
                onRefreshOpenRouterModels = onRefreshOpenRouterModels
            )
            TranscriptionTab.TOOLS -> Text(
                "Tools — coming soon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Common test panel — visible from BOTH tabs. Tests whatever is
        // currently the active source: cloud (whichever provider/model is
        // configured) or on-device (whichever Whisper model is selected).
        // Replaces the standalone Tools tab.
        Button(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionTestState !is ConnectionTestState.Testing
        ) {
            Text(
                if (connectionTestState is ConnectionTestState.Testing) "Testing…"
                else "Test transcription"
            )
        }
        OutlinedButton(
            onClick = onShowApiCallLogs,
            modifier = Modifier.fillMaxWidth()
        ) { Text("View API logs") }
        TestLogPanel(
            entries = transcriptionTestLog,
            state = connectionTestState,
            autoCloseOnSuccess = false,
            onDismiss = onResetConnectionState,
            runningPlaceholder = "Preparing test…"
        )
    }
}

@Composable
private fun SourcePivot(
    selected: TranscriptionTab,
    onSelect: (TranscriptionTab) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            PivotChip(
                selected = selected == TranscriptionTab.CLOUD,
                label = "Cloud",
                icon = Icons.Outlined.Cloud,
                onClick = { onSelect(TranscriptionTab.CLOUD) },
                modifier = Modifier.weight(1f)
            )
            PivotChip(
                selected = selected == TranscriptionTab.LOCAL,
                label = "Local",
                icon = Icons.Outlined.PhoneAndroid,
                onClick = { onSelect(TranscriptionTab.LOCAL) },
                modifier = Modifier.weight(1f)
            )
            PivotChip(
                selected = selected == TranscriptionTab.TOOLS,
                label = "Tools",
                icon = Icons.Outlined.Build,
                onClick = { onSelect(TranscriptionTab.TOOLS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PivotChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// region Cloud panel

@Composable
private fun CloudPanel(
    apiSettings: ApiSettings,
    onSave: (ApiProvider, String, String, Boolean, String, String, String) -> Unit,
    onShowProviderKeyHelp: () -> Unit,
    onSetActive: () -> Unit,
    openRouterModels: List<OpenRouterModelInfo> = emptyList(),
    openRouterRefreshing: Boolean = false,
    openRouterError: String? = null,
    onRefreshOpenRouterModels: () -> Unit = {}
) {
    var provider by remember(apiSettings.provider) { mutableStateOf(apiSettings.provider) }
    var baseUrl by remember(apiSettings) { mutableStateOf(apiSettings.getCurrentBaseUrl()) }
    var apiKey by remember(apiSettings) { mutableStateOf(apiSettings.getCurrentApiKey()) }
    var requiresAuth by remember(apiSettings) { mutableStateOf(apiSettings.getCurrentRequiresAuth()) }
    var modelId by remember(apiSettings) { mutableStateOf(apiSettings.modelId) }
    var inputLanguage by remember(apiSettings) { mutableStateOf(apiSettings.inputLanguage) }
    var outputLanguage by remember(apiSettings) { mutableStateOf(apiSettings.outputLanguage) }
    var showAdvanced by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    val cloudActive = !apiSettings.localModelSettings.useLocalWhisper

    fun persist() {
        onSave(provider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ActiveSourceCard(
            isActive = cloudActive,
            activeText = "Cloud is the active transcription source.",
            inactiveText = "Cloud configuration saved but not active. Use this to override the on-device model.",
            ctaLabel = "Set cloud as active",
            onSetActive = onSetActive
        )

        FieldGroup(title = "Provider") {
            CloudProviderSelector(
                selectedProvider = provider,
                onProviderSelected = { newProvider ->
                    provider = newProvider
                    val newApiKey = apiSettings.apiKeys[newProvider] ?: ""
                    val newConfig = apiSettings.providerConfigs[newProvider]
                    apiKey = newApiKey
                    baseUrl = newConfig?.customBaseUrl?.ifEmpty { newProvider.defaultEndpoint }
                        ?: newProvider.defaultEndpoint
                    requiresAuth = newConfig?.requiresAuth ?: newProvider.requiresAuth
                    if (modelId.isEmpty() || !newProvider.defaultModels.contains(modelId)) {
                        modelId = newProvider.defaultModels.firstOrNull() ?: modelId
                    }
                    onSave(newProvider, baseUrl, apiKey, requiresAuth, modelId, inputLanguage, outputLanguage)
                }
            )
        }

        FieldGroup(title = "Model") {
            ModelSelector(
                provider = provider,
                selectedModel = modelId,
                availableModels = provider.defaultModels,
                onModelSelected = {
                    modelId = it
                    persist()
                }
            )
        }

        if (provider == ApiProvider.OPENROUTER) {
            OpenRouterDiscoveryPanel(
                models = openRouterModels,
                refreshing = openRouterRefreshing,
                error = openRouterError,
                selectedModelId = modelId,
                onRefresh = onRefreshOpenRouterModels,
                onSelect = {
                    modelId = it
                    persist()
                }
            )
        }

        FieldGroup(title = "API key") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    persist()
                },
                singleLine = true,
                placeholder = { Text(if (requiresAuth) "Required" else "Optional") },
                visualTransformation = if (apiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                contentDescription = if (apiKeyVisible) "Hide" else "Show"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowProviderKeyHelp,
                    modifier = Modifier.weight(1f)
                ) { Text("How to get a key") }
            }
        }

        FieldGroup(title = "Languages") {
            LanguageSelector(
                selectedLanguage = inputLanguage,
                onLanguageSelected = {
                    inputLanguage = it
                    persist()
                },
                label = "Input language",
                supportingText = "Spoken language (auto-detect supported)"
            )
            Spacer(Modifier.height(8.dp))
            LanguageSelector(
                selectedLanguage = outputLanguage,
                onLanguageSelected = {
                    outputLanguage = it
                    persist()
                },
                label = "Output language",
                supportingText = "Translate transcription (leave blank to keep original)"
            )
        }

        // Advanced (collapsed by default)
        AdvancedFieldGroup(
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced }
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    persist()
                },
                singleLine = true,
                label = { Text("API endpoint") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Requires API key", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Disable for self-hosted servers without auth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = requiresAuth,
                    onCheckedChange = {
                        requiresAuth = it
                        persist()
                    }
                )
            }
        }

    }
}

// endregion

// endregion

// region Shared test log panel

@Composable
fun TestLogPanel(
    entries: List<TestLogEntry>,
    state: ConnectionTestState,
    autoCloseOnSuccess: Boolean,
    onDismiss: () -> Unit,
    runningPlaceholder: String = "Working…"
) {
    val visible = state !is ConnectionTestState.Idle || entries.isNotEmpty()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    is ConnectionTestState.Success -> MaterialTheme.colorScheme.tertiaryContainer
                    is ConnectionTestState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (state) {
                        is ConnectionTestState.Testing -> {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(runningPlaceholder, fontWeight = FontWeight.SemiBold)
                        }
                        is ConnectionTestState.Success -> {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Success",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        is ConnectionTestState.Error -> {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Failed",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        else -> {}
                    }
                    Spacer(Modifier.weight(1f))
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val text = buildTestLogText(state, entries)
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "Test log copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy log",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                    }
                }

                if (entries.isNotEmpty()) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            entries.takeLast(40).forEach { entry ->
                                TestLogRow(entry)
                            }
                        }
                    }
                }
            }
        }
        if (autoCloseOnSuccess && state is ConnectionTestState.Success) {
            LaunchedEffect(state) {
                delay(4000)
                onDismiss()
            }
        }
    }
}

private fun buildTestLogText(state: ConnectionTestState, entries: List<TestLogEntry>): String {
    val sb = StringBuilder()
    val header = when (state) {
        is ConnectionTestState.Testing -> "RUNNING"
        is ConnectionTestState.Success -> "SUCCESS — ${state.message}"
        is ConnectionTestState.Error -> "FAILED — ${state.message}"
        else -> "IDLE"
    }
    sb.appendLine("HyperWhisper test log — $header")
    entries.forEach { e ->
        sb.append('[').append(formatTestTimestamp(e.timestamp)).append("] ")
        sb.append(e.level.name).append(": ").append(e.message)
        e.detail?.let { sb.append(" — ").append(it) }
        sb.append('\n')
    }
    return sb.toString()
}

@Composable
private fun TestLogRow(entry: TestLogEntry) {
    val (icon, tint) = when (entry.level) {
        TestLogLevel.RUNNING -> Icons.Outlined.HourglassTop to MaterialTheme.colorScheme.primary
        TestLogLevel.OK -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.tertiary
        TestLogLevel.FAIL -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        TestLogLevel.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatTestTimestamp(entry.timestamp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            entry.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val testTimeFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm:ss", Locale.US)
}
private fun formatTestTimestamp(epochMs: Long): String = testTimeFormat.format(Date(epochMs))

// endregion

// region On-device panel

@Composable
private fun LocalWhisperPanel(
    settings: LocalModelSettings,
    discoveredModels: List<LocalModelInfo>,
    onUpdate: (LocalModelSettings) -> Unit,
    onDiscover: () -> Unit,
    onVerify: (String) -> Unit,
    onSetActiveLocalModel: (String) -> Unit
) {
    val context = LocalContext.current
    var hasFullStorageAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else true
        )
    }

    val localActive = settings.useLocalWhisper && settings.whisperModelPath.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ActiveSourceCard(
            isActive = localActive,
            activeText = "On-device Whisper is active: ${settings.whisperModelPath.substringAfterLast('/')}",
            inactiveText = "Pick a model below — tap “Use this model” to make it the active transcription source.",
            ctaLabel = null,
            onSetActive = {}
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasFullStorageAccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage access required",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "To find models in Downloads and other shared folders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.addCategory("android.intent.category.DEFAULT")
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Grant access") }
                }
            }
        }

        FieldGroup(title = "Whisper models") {
            if (discoveredModels.isEmpty()) {
                Text(
                    "No models found. Place .bin or .gguf files in Downloads/Models.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                discoveredModels.forEach { model ->
                    LocalModelRow(
                        model = model,
                        isSelected = model.path == settings.whisperModelPath,
                        isActiveSource = localActive && model.path == settings.whisperModelPath,
                        onSelect = { onUpdate(settings.copy(whisperModelPath = model.path)) },
                        onSetActive = { onSetActiveLocalModel(model.path) },
                        onVerify = { onVerify(model.path) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscover,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rescan")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OutlinedButton(
                        onClick = {
                            hasFullStorageAccess = Environment.isExternalStorageManager()
                            onDiscover()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Refresh access") }
                }
            }
        }

        FieldGroup(title = "Performance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-discover", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Scan common folders for model files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoDiscover,
                    onCheckedChange = { onUpdate(settings.copy(autoDiscover = it)) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Processing threads", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Higher = faster, more battery (1–16)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var threadsText by remember(settings.threads) { mutableStateOf(settings.threads.toString()) }
                OutlinedTextField(
                    value = threadsText,
                    onValueChange = {
                        threadsText = it
                        it.toIntOrNull()?.let { num ->
                            onUpdate(settings.copy(threads = num.coerceIn(1, 16)))
                        }
                    },
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun LocalModelRow(
    model: LocalModelInfo,
    isSelected: Boolean,
    isActiveSource: Boolean,
    onSelect: () -> Unit,
    onSetActive: () -> Unit,
    onVerify: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = when {
            isActiveSource -> MaterialTheme.colorScheme.primaryContainer
            isSelected -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isSelected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontWeight = FontWeight.SemiBold)
                        if (isActiveSource) {
                            Spacer(Modifier.width(8.dp))
                            ActiveBadge()
                        }
                    }
                    Text(
                        "${model.sizeBytes / 1024 / 1024} MB · …${model.path.takeLast(28)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onVerify) {
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = "Verify integrity",
                        tint = if (model.hash.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!isActiveSource) {
                Button(
                    onClick = onSetActive,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Use this model") }
            }
        }
    }
}

@Composable
private fun ActiveBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "Active",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActiveSourceCard(
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    ctaLabel: String?,
    onSetActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isActive) activeText else inactiveText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!isActive && !ctaLabel.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSetActive) { Text(ctaLabel) }
            }
        }
    }
}

// endregion


// region Field group helpers

@Composable
private fun FieldGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun AdvancedFieldGroup(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Surface(
            onClick = onToggle,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

// endregion
