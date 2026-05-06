package com.hyperwhisper.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.security.LocalSecretsReveal
import com.hyperwhisper.security.SecretsRevealController
import com.hyperwhisper.security.SecureClipboard
import com.hyperwhisper.ui.about.AboutActivity
import com.hyperwhisper.ui.settings.dialogs.AddModeDialog
import com.hyperwhisper.ui.settings.dialogs.EditModeDialog
import com.hyperwhisper.ui.settings.dialogs.ProviderKeyInstructionsDialog
import com.hyperwhisper.ui.settings.sections.AppUpdateSection
import com.hyperwhisper.ui.settings.sections.AppearanceSection
import com.hyperwhisper.ui.settings.sections.KeyboardBehaviorSection
import com.hyperwhisper.ui.settings.sections.LlmConfigSection
import com.hyperwhisper.ui.settings.sections.TranscriptionSection
import com.hyperwhisper.ui.settings.sections.VoiceModesSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    initialProvider: ApiProvider? = null,
    updateManager: com.hyperwhisper.ime.update.UpdateManager? = null,
    onShowUpdateDialog: (com.hyperwhisper.ime.update.UpdateInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val apiSettings by viewModel.apiSettings.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()
    val appearanceSettings by viewModel.appearanceSettings.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val postProcessingTestState by viewModel.postProcessingTestState.collectAsState()
    val transcriptionTestLog by viewModel.transcriptionTestLog.collectAsState()
    val postProcessingTestLog by viewModel.postProcessingTestLog.collectAsState()
    val apiCallLogs by viewModel.apiCallLogs.collectAsState()
    val apiCallStatistics by viewModel.apiCallStatistics.collectAsState()
    val discoveredModels by viewModel.discoveredModels.collectAsState()
    val whisperDownloadStates by viewModel.whisperDownloadStates.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val openRouterRefreshing by viewModel.openRouterRefreshing.collectAsState()
    val openRouterError by viewModel.openRouterError.collectAsState()

    val context = LocalContext.current
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    val secretsReveal = LocalSecretsReveal.current

    fun gateAndExportSecrets() {
        secretsReveal.request(
            title = strings.secretsGateExportTitle,
            subtitle = strings.secretsGateSubtitle,
            onGranted = {
                val exportJson = viewModel.buildSecretsExportJson()
                SecureClipboard.copySensitive(
                    context = context,
                    label = "hyperwhisper-secrets",
                    value = exportJson,
                )
                Toast.makeText(
                    context,
                    strings.settingsSecretsCopiedToast,
                    Toast.LENGTH_LONG,
                ).show()
            },
            onDenied = { reason ->
                val msg = when (reason) {
                    SecretsRevealController.Denial.NOT_ENROLLED ->
                        strings.secretsGateNotEnrolledMessage
                    SecretsRevealController.Denial.UNAVAILABLE ->
                        strings.secretsGateUnavailableMessage
                    SecretsRevealController.Denial.CANCELLED,
                    SecretsRevealController.Denial.FAILED -> null
                }
                if (msg != null) {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    var route by remember(initialProvider) {
        mutableStateOf<SettingsRoute>(
            if (initialProvider != null) SettingsRoute.Detail(SettingsCategory.TRANSCRIPTION)
            else SettingsRoute.Home
        )
    }

    // Apply initial provider once
    var initialProviderApplied by remember { mutableStateOf(false) }
    LaunchedEffect(initialProvider, apiSettings) {
        if (initialProvider != null && !initialProviderApplied) {
            initialProviderApplied = true
            // Persist via VM so the rest of the app picks it up
            viewModel.saveApiSettings(
                provider = initialProvider,
                baseUrl = apiSettings.providerConfigs[initialProvider]?.customBaseUrl?.ifEmpty { initialProvider.defaultEndpoint }
                    ?: initialProvider.defaultEndpoint,
                apiKey = apiSettings.apiKeys[initialProvider] ?: "",
                requiresAuth = apiSettings.providerConfigs[initialProvider]?.requiresAuth ?: initialProvider.requiresAuth,
                modelId = apiSettings.modelId.ifEmpty { initialProvider.defaultModels.firstOrNull() ?: "" },
                inputLanguage = apiSettings.inputLanguage,
                outputLanguage = apiSettings.outputLanguage
            )
        }
    }

    BackHandler(enabled = route is SettingsRoute.Detail) {
        route = SettingsRoute.Home
    }

    // Cross-cutting dialogs
    var showProviderKeyHelp by remember { mutableStateOf(false) }
    var showApiCallLogs by remember { mutableStateOf(false) }
    var showAddModeDialog by remember { mutableStateOf(false) }
    var editingMode by remember { mutableStateOf<com.hyperwhisper.data.VoiceMode?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val current = route
                    val title = if (current is SettingsRoute.Detail) current.category.localizedTitle()
                        else strings.settings
                    val subtitle: String? = when {
                        current is SettingsRoute.Detail &&
                            current.category == SettingsCategory.TRANSCRIPTION ->
                            strings.settingsActivePrefix + SettingsStatusLabels.transcriptionLabel(apiSettings)
                        current is SettingsRoute.Detail &&
                            current.category == SettingsCategory.POST_PROCESSING ->
                            strings.settingsActivePrefix + SettingsStatusLabels.postProcessingLabel(apiSettings)
                        else -> null
                    }
                    androidx.compose.foundation.layout.Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    val current = route
                    if (current is SettingsRoute.Detail) {
                        IconButton(onClick = { route = SettingsRoute.Home }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    } else {
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = strings.settingsOverflowMoreDesc)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.settingsOverflowAbout) },
                                onClick = {
                                    overflowOpen = false
                                    context.startActivity(Intent(context, AboutActivity::class.java))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.viewApiLogs) },
                                onClick = {
                                    overflowOpen = false
                                    showApiCallLogs = true
                                }
                            )
                            if (appearanceSettings.techieModeEnabled) {
                                DropdownMenuItem(
                                    text = { Text(strings.settingsOverflowExportSecrets) },
                                    onClick = {
                                        overflowOpen = false
                                        gateAndExportSecrets()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val targetIsHome = targetState is SettingsRoute.Home
                    val transform = if (targetIsHome) {
                        slideInHorizontally { -it / 4 } + fadeIn() togetherWith
                            slideOutHorizontally { it / 4 } + fadeOut()
                    } else {
                        slideInHorizontally { it / 4 } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 4 } + fadeOut()
                    }
                    transform.using(SizeTransform(clip = false))
                },
                label = "settings-route"
            ) { current ->
                when (current) {
                    is SettingsRoute.Home -> {
                        val retestProgress by viewModel.retestProgress.collectAsState()
                        val retestRunning by viewModel.retestRunning.collectAsState()
                        SettingsHomeScreen(
                            apiSettings = apiSettings,
                            retestProgress = retestProgress,
                            retestRunning = retestRunning,
                            onRetestAll = { viewModel.retestAllProviders() },
                            onCategorySelected = { route = SettingsRoute.Detail(it) }
                        )
                    }
                    is SettingsRoute.Detail -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (current.category) {
                            SettingsCategory.TRANSCRIPTION -> TranscriptionSection(
                                apiSettings = apiSettings,
                                discoveredModels = discoveredModels,
                                connectionTestState = connectionTestState,
                                transcriptionTestLog = transcriptionTestLog,
                                whisperDownloadStates = whisperDownloadStates,
                                onSaveCloud = { p, b, k, r, m, i, o ->
                                    viewModel.saveApiSettings(p, b, k, r, m, i, o)
                                },
                                onUpdateLocalSettings = { viewModel.updateLocalModelSettings(it) },
                                onDiscoverModels = { viewModel.discoverModels() },
                                onVerifyModel = { viewModel.verifyModelIntegrity(it) },
                                onTestConnection = {
                                    val s = apiSettings
                                    viewModel.testConnection(
                                        s.provider, s.getCurrentBaseUrl(), s.getCurrentApiKey(), s.modelId
                                    )
                                },
                                onResetConnectionState = { viewModel.resetConnectionTestState() },
                                onShowProviderKeyHelp = { showProviderKeyHelp = true },
                                onShowApiCallLogs = { showApiCallLogs = true },
                                onSetActiveCloud = { viewModel.setActiveCloudProvider() },
                                onSetActiveLocalModel = { viewModel.setActiveLocalWhisperModel(it) },
                                onStartWhisperDownload = { viewModel.startWhisperDownload(it) },
                                onCancelWhisperDownload = { viewModel.cancelWhisperDownload(it) },
                                onDeleteDownloadedWhisper = { viewModel.deleteDownloadedWhisperModel(it) },
                                openRouterModels = openRouterModels,
                                openRouterRefreshing = openRouterRefreshing,
                                openRouterError = openRouterError,
                                onRefreshOpenRouterModels = { viewModel.refreshOpenRouterModels() }
                            )

                            SettingsCategory.POST_PROCESSING -> PostProcessingDetail(
                                apiSettings = apiSettings,
                                onConfigChange = { viewModel.updateLlmConfig(it) },
                                onUpdateProviderApiKey = { p, k -> viewModel.updateProviderApiKey(p, k) },
                                postProcessingTestState = postProcessingTestState,
                                postProcessingTestLog = postProcessingTestLog,
                                onTestPostProcessing = {
                                    val mode = voiceModes.firstOrNull { !it.id.equals("verbatim", ignoreCase = true) }
                                        ?: voiceModes.firstOrNull()
                                        ?: com.hyperwhisper.data.VoiceMode(
                                            id = "test", name = "Test", systemPrompt = ""
                                        )
                                    viewModel.testPostProcessing(mode)
                                },
                                onResetPostProcessingTestState = { viewModel.resetPostProcessingTestState() },
                                openRouterModels = openRouterModels,
                                openRouterRefreshing = openRouterRefreshing,
                                openRouterError = openRouterError,
                                onRefreshOpenRouterModels = { viewModel.refreshOpenRouterModels() }
                            )

                            SettingsCategory.LOCAL_MODELS -> {
                                val gemmaStates by viewModel.gemmaDownloadStates.collectAsState()
                                val integrationResults by viewModel.integrationResults.collectAsState()
                                val integrationRunning by viewModel.integrationRunning.collectAsState()
                                val discoveredModels by viewModel.discoveredModels.collectAsState()
                                com.hyperwhisper.ui.settings.sections.LocalModelsSection(
                                    whisperStates = whisperDownloadStates,
                                    gemmaStates = gemmaStates,
                                    activeWhisperPath = apiSettings.localModelSettings.whisperModelPath,
                                    activeGemmaPath = apiSettings.localModelSettings.gemmaModelPath,
                                    useLocalWhisper = apiSettings.localModelSettings.useLocalWhisper,
                                    useLocalGemma = apiSettings.localModelSettings.useLocalGemma,
                                    onStartWhisperDownload = { viewModel.startWhisperDownload(it) },
                                    onCancelWhisperDownload = { viewModel.cancelWhisperDownload(it) },
                                    onDeleteWhisperDownload = { viewModel.deleteDownloadedWhisperModel(it) },
                                    onSetActiveWhisper = { viewModel.setActiveLocalWhisperModel(it) },
                                    onStartGemmaDownload = { viewModel.startGemmaDownload(it) },
                                    onCancelGemmaDownload = { viewModel.cancelGemmaDownload(it) },
                                    onDeleteGemmaDownload = { viewModel.deleteDownloadedGemmaModel(it) },
                                    onSetActiveGemma = { viewModel.setActiveGemmaModel(it) },
                                    detectedGemmaFiles = discoveredModels.filter {
                                        it.type == com.hyperwhisper.data.LocalModelType.GEMMA
                                    },
                                    onDeleteOnDiskFile = { viewModel.deleteOnDiskFile(it) },
                                    onRescanOnDisk = { viewModel.discoverModels() },
                                    integrationResults = integrationResults,
                                    integrationRunning = integrationRunning,
                                    onRunIntegrationTests = { viewModel.runIntegrationTests() },
                                    onOpenProviderConfiguration = { provider ->
                                        viewModel.saveApiSettings(
                                            provider = provider,
                                            baseUrl = apiSettings.providerConfigs[provider]?.customBaseUrl?.ifEmpty {
                                                provider.defaultEndpoint
                                            } ?: provider.defaultEndpoint,
                                            apiKey = apiSettings.apiKeys[provider] ?: "",
                                            requiresAuth = apiSettings.providerConfigs[provider]?.requiresAuth
                                                ?: provider.requiresAuth,
                                            modelId = apiSettings.modelId.ifEmpty {
                                                provider.defaultModels.firstOrNull() ?: ""
                                            },
                                            inputLanguage = apiSettings.inputLanguage,
                                            outputLanguage = apiSettings.outputLanguage
                                        )
                                        route = SettingsRoute.Detail(SettingsCategory.TRANSCRIPTION)
                                    }
                                )
                            }

                            SettingsCategory.VOICE_MODES -> Box(modifier = Modifier.padding(16.dp)) {
                                androidx.compose.foundation.layout.Column {
                                    VoiceModesSection(
                                        voiceModes = voiceModes,
                                        onAddMode = { showAddModeDialog = true },
                                        onEditMode = { editingMode = it },
                                        onDeleteMode = { viewModel.deleteVoiceMode(it) }
                                    )
                                }
                            }

                            SettingsCategory.KEYBOARD_BEHAVIOR -> KeyboardBehaviorSection(
                                appearanceSettings = appearanceSettings,
                                onSettingsChange = { viewModel.saveAppearanceSettings(it) }
                            )

                            SettingsCategory.APPEARANCE -> AppearanceSection(
                                appearanceSettings = appearanceSettings,
                                onSettingsChange = { viewModel.saveAppearanceSettings(it) }
                            )

                            SettingsCategory.ADVANCED -> AdvancedDetail(
                                onOpenApiLogs = { showApiCallLogs = true },
                                onOpenProviderKeyHelp = { showProviderKeyHelp = true },
                                techieModeEnabled = appearanceSettings.techieModeEnabled,
                                onExportSecrets = { gateAndExportSecrets() }
                            )

                            SettingsCategory.ABOUT -> {
                                LaunchedEffect(Unit) {
                                    context.startActivity(Intent(context, AboutActivity::class.java))
                                    route = SettingsRoute.Home
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showProviderKeyHelp) {
        ProviderKeyInstructionsDialog(
            provider = apiSettings.provider,
            onDismiss = { showProviderKeyHelp = false }
        )
    }

    if (showApiCallLogs) {
        ApiCallLogsScreen(
            logs = apiCallLogs,
            statistics = apiCallStatistics,
            onClearLogs = {
                coroutineScope.launch { viewModel.clearApiCallLogs() }
            },
            onDismiss = { showApiCallLogs = false }
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
            onUpdate = { updated ->
                viewModel.updateVoiceMode(updated)
                editingMode = null
            }
        )
    }
}

/**
 * Map an [LlmProvider] to the matching [ApiProvider] (transcription side) so
 * we can share API keys between the two configs. Providers that don't have
 * a sibling on the other side return null.
 */
private fun llmToApiProviderMatch(llm: LlmProvider): ApiProvider? = when (llm) {
    LlmProvider.OPENAI -> ApiProvider.OPENAI
    LlmProvider.OPENROUTER -> ApiProvider.OPENROUTER
    LlmProvider.GEMINI -> ApiProvider.GEMINI
    LlmProvider.GROQ -> ApiProvider.GROQ
    LlmProvider.DEEPSEEK -> ApiProvider.DEEPSEEK
    LlmProvider.MISTRAL -> ApiProvider.MISTRAL
    else -> null
}

@Composable
private fun PostProcessingDetail(
    apiSettings: com.hyperwhisper.data.ApiSettings,
    onConfigChange: (LlmConfig) -> Unit,
    onUpdateProviderApiKey: (ApiProvider, String) -> Unit = { _, _ -> },
    postProcessingTestState: com.hyperwhisper.ui.settings.ConnectionTestState =
        com.hyperwhisper.ui.settings.ConnectionTestState.Idle,
    postProcessingTestLog: List<com.hyperwhisper.ui.settings.TestLogEntry> = emptyList(),
    onTestPostProcessing: () -> Unit = {},
    onResetPostProcessingTestState: () -> Unit = {},
    openRouterModels: List<com.hyperwhisper.network.OpenRouterModelInfo> = emptyList(),
    openRouterRefreshing: Boolean = false,
    openRouterError: String? = null,
    onRefreshOpenRouterModels: () -> Unit = {}
) {
    val cfg = apiSettings.llmConfig
    var llmProvider by remember(cfg) { mutableStateOf(cfg.provider) }
    var llmBaseUrl by remember(cfg) { mutableStateOf(cfg.getCurrentBaseUrl()) }
    var llmApiKey by remember(cfg) { mutableStateOf(cfg.getCurrentApiKey()) }
    var llmRequiresAuth by remember(cfg) { mutableStateOf(cfg.getCurrentRequiresAuth()) }
    var llmModelId by remember(cfg) { mutableStateOf(cfg.modelId) }

    // Compose the per-provider maps for save: the local fields shown in the UI
    // belong to the currently active LLM provider — fold them into the persisted
    // maps so previously-entered keys for other providers survive switching.
    fun persist() {
        val updatedKeys = cfg.apiKeys.toMutableMap().apply {
            val trimmed = llmApiKey.trim()
            if (trimmed.isEmpty()) remove(llmProvider) else put(llmProvider, trimmed)
        }
        val updatedConfigs = cfg.providerConfigs.toMutableMap().apply {
            val normalizedUrl = if (llmBaseUrl.isNotEmpty() && !llmBaseUrl.endsWith("/"))
                llmBaseUrl + "/" else llmBaseUrl
            put(llmProvider, com.hyperwhisper.data.LlmProviderConfig(
                customBaseUrl = normalizedUrl,
                requiresAuth = if (llmRequiresAuth == llmProvider.requiresAuth) null
                    else llmRequiresAuth,
            ))
        }
        onConfigChange(
            LlmConfig(
                provider = llmProvider,
                customBaseUrl = llmBaseUrl,
                apiKey = llmApiKey,
                requiresAuth = llmRequiresAuth,
                modelId = llmModelId,
                apiKeys = updatedKeys,
                providerConfigs = updatedConfigs,
            )
        )
    }

    Box(modifier = Modifier.padding(16.dp)) {
        LlmConfigSection(
            llmProvider = llmProvider,
            llmBaseUrl = llmBaseUrl,
            llmApiKey = llmApiKey,
            llmRequiresAuth = llmRequiresAuth,
            llmModelId = llmModelId,
            // Surface the transcription-side key for the *matching* provider
            // (LLM OPENROUTER ↔ ApiProvider OPENROUTER, etc.), not whichever
            // transcription provider happens to be active. Lets the user
            // reuse keys across the two configs without retyping.
            providerApiKey = run {
                val matched = llmToApiProviderMatch(llmProvider)
                matched?.let { apiSettings.apiKeys[it].orEmpty() }.orEmpty()
            },
            onLlmProviderChange = { newProvider ->
                // Save the current view's edits into the per-provider maps for
                // the OLD provider before swapping to the NEW provider's stored
                // values. Without this step a user editing a key and then
                // switching provider would silently lose the unflushed edit.
                val savedKeys = cfg.apiKeys.toMutableMap().apply {
                    val trimmed = llmApiKey.trim()
                    if (trimmed.isEmpty()) remove(llmProvider) else put(llmProvider, trimmed)
                }
                val savedConfigs = cfg.providerConfigs.toMutableMap().apply {
                    val normalizedUrl = if (llmBaseUrl.isNotEmpty() && !llmBaseUrl.endsWith("/"))
                        llmBaseUrl + "/" else llmBaseUrl
                    put(llmProvider, com.hyperwhisper.data.LlmProviderConfig(
                        customBaseUrl = normalizedUrl,
                        requiresAuth = if (llmRequiresAuth == llmProvider.requiresAuth) null
                            else llmRequiresAuth,
                    ))
                }

                llmProvider = newProvider
                // Load the NEW provider's stored values, falling back to the
                // provider's defaults when nothing is stored yet.
                llmApiKey = savedKeys[newProvider].orEmpty()
                val storedConfig = savedConfigs[newProvider]
                llmBaseUrl = storedConfig?.customBaseUrl?.takeIf { it.isNotEmpty() }
                    ?: newProvider.defaultEndpoint
                llmRequiresAuth = storedConfig?.requiresAuth ?: newProvider.requiresAuth
                if (llmModelId.isEmpty() || !newProvider.defaultModels.contains(llmModelId)) {
                    llmModelId = newProvider.defaultModels.firstOrNull() ?: llmModelId
                }

                onConfigChange(
                    LlmConfig(
                        provider = newProvider,
                        customBaseUrl = llmBaseUrl,
                        apiKey = llmApiKey,
                        requiresAuth = llmRequiresAuth,
                        modelId = llmModelId,
                        apiKeys = savedKeys,
                        providerConfigs = savedConfigs,
                    )
                )
            },
            onLlmBaseUrlChange = { llmBaseUrl = it; persist() },
            onLlmApiKeyChange = { llmApiKey = it; persist() },
            onLlmRequiresAuthChange = { llmRequiresAuth = it; persist() },
            onLlmModelIdChange = { llmModelId = it; persist() },
            onResetLlmDefaults = {
                llmBaseUrl = llmProvider.defaultEndpoint
                llmRequiresAuth = llmProvider.requiresAuth
                llmModelId = llmProvider.defaultModels.firstOrNull() ?: llmModelId
                persist()
            },
            onReuseLlmKeyForProvider = {
                val matched = llmToApiProviderMatch(llmProvider) ?: return@LlmConfigSection
                val transcriptionKey = apiSettings.apiKeys[matched].orEmpty()
                if (llmApiKey.isBlank() && transcriptionKey.isNotBlank()) {
                    llmApiKey = transcriptionKey
                    persist()
                } else if (llmApiKey.isNotBlank() && transcriptionKey.isBlank()) {
                    onUpdateProviderApiKey(matched, llmApiKey)
                }
            },
            onShowLlmInfo = { /* deprecated info dialog removed in new UI */ },
            postProcessingTestState = postProcessingTestState,
            postProcessingTestLog = postProcessingTestLog,
            onTestPostProcessing = onTestPostProcessing,
            onResetPostProcessingTestState = onResetPostProcessingTestState,
            openRouterModels = openRouterModels,
            openRouterRefreshing = openRouterRefreshing,
            openRouterError = openRouterError,
            onRefreshOpenRouterModels = onRefreshOpenRouterModels,
            localGemmaModelPath = apiSettings.localModelSettings.gemmaModelPath
        )
    }
}

@Composable
private fun AdvancedDetail(
    onOpenApiLogs: () -> Unit,
    onOpenProviderKeyHelp: () -> Unit,
    techieModeEnabled: Boolean,
    onExportSecrets: () -> Unit
) {
    val strings = LocalStrings.current
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        AdvancedRow(
            title = strings.advancedApiLogsTitle,
            description = strings.advancedApiLogsDescription,
            onClick = onOpenApiLogs
        )
        AdvancedRow(
            title = strings.advancedProviderKeyHelpTitle,
            description = strings.advancedProviderKeyHelpDescription,
            onClick = onOpenProviderKeyHelp
        )
        if (techieModeEnabled) {
            AdvancedRow(
                title = strings.advancedExportSecretsTitle,
                description = strings.advancedExportSecretsDescription,
                onClick = onExportSecrets
            )
        }
    }
}

@Composable
private fun AdvancedRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(0.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
