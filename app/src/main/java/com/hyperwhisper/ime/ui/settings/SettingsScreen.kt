package com.hyperwhisper.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import com.hyperwhisper.ui.settings.components.SettingsHamburgerMenu
import com.hyperwhisper.ui.settings.sections.LocalModelsSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.about.AboutActivity
import com.hyperwhisper.ui.settings.components.cards.SectionCard
import com.hyperwhisper.ui.settings.dialogs.AddModeDialog
import com.hyperwhisper.ui.settings.dialogs.EditModeDialog
import com.hyperwhisper.ui.settings.dialogs.InputLanguageInfoDialog
import com.hyperwhisper.ui.settings.dialogs.LogsInfoDialog
import com.hyperwhisper.ui.settings.dialogs.ModelInfoDialog
import com.hyperwhisper.ui.settings.dialogs.ProviderKeyInstructionsDialog
import com.hyperwhisper.ui.settings.sections.ApiConfigSection
import com.hyperwhisper.ui.settings.sections.AppearanceSection
import com.hyperwhisper.ui.settings.sections.VoiceModesSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val apiCallLogs by viewModel.apiCallLogs.collectAsState()
    val apiCallStatistics by viewModel.apiCallStatistics.collectAsState()
    val discoveredModels by viewModel.discoveredModels.collectAsState()

    // Navigation and Pager state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val pagerState = rememberPagerState(pageCount = { SettingsTab.values().size })
    val selectedTab = SettingsTab.values()[pagerState.currentPage]

    // Local form state for API settings
    var provider by remember { mutableStateOf(initialProvider ?: apiSettings.provider) }
    var baseUrl by remember { mutableStateOf(apiSettings.getCurrentBaseUrl()) }
    var apiKey by remember { mutableStateOf(apiSettings.getCurrentApiKey()) }
    var requiresAuth by remember { mutableStateOf(apiSettings.getCurrentRequiresAuth()) }
    var modelId by remember { mutableStateOf(apiSettings.modelId) }
    var inputLanguage by remember { mutableStateOf(apiSettings.inputLanguage) }
    var outputLanguage by remember { mutableStateOf(apiSettings.outputLanguage) }

    // Local form state for LLM settings
    var llmProvider by remember { mutableStateOf(apiSettings.llmConfig.provider) }
    var llmBaseUrl by remember { mutableStateOf(apiSettings.llmConfig.customBaseUrl) }
    var llmApiKey by remember { mutableStateOf(apiSettings.llmConfig.apiKey) }
    var llmRequiresAuth by remember { mutableStateOf(apiSettings.llmConfig.requiresAuth) }
    var llmModelId by remember { mutableStateOf(apiSettings.llmConfig.modelId) }

    // Dialog visibility state
    var showInputLanguageInfo by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }
    var showLlmInfo by remember { mutableStateOf(false) }
    var showAddModeDialog by remember { mutableStateOf(false) }
    var editingMode by remember { mutableStateOf<VoiceMode?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showProviderKeyHelp by remember { mutableStateOf(false) }
    var showApiCallLogs by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var initialProviderApplied by remember { mutableStateOf(false) }

    // Synchronize local state when settings change externally
    LaunchedEffect(apiSettings, initialProvider) {
        if (initialProvider != null && !initialProviderApplied) {
            provider = initialProvider
            initialProviderApplied = true
        } else if (initialProvider == null) {
            provider = apiSettings.provider
        }
        baseUrl = apiSettings.getCurrentBaseUrl()
        apiKey = apiSettings.getCurrentApiKey()
        requiresAuth = apiSettings.getCurrentRequiresAuth()
        modelId = apiSettings.modelId
        inputLanguage = apiSettings.inputLanguage
        outputLanguage = apiSettings.outputLanguage

        // LLM settings
        llmProvider = apiSettings.llmConfig.provider
        llmBaseUrl = apiSettings.llmConfig.customBaseUrl
        llmApiKey = apiSettings.llmConfig.apiKey
        llmRequiresAuth = apiSettings.llmConfig.requiresAuth
        llmModelId = apiSettings.llmConfig.modelId
    }

    // Update API key and defaults when provider changes
    LaunchedEffect(provider) {
        apiKey = apiSettings.apiKeys[provider] ?: ""

        // Get provider-specific config or use defaults
        val providerConfig = apiSettings.providerConfigs[provider]
        baseUrl = providerConfig?.customBaseUrl?.ifEmpty { provider.defaultEndpoint }
            ?: provider.defaultEndpoint
        requiresAuth = providerConfig?.requiresAuth ?: provider.requiresAuth

        // Auto-select first model for provider
        if (modelId.isEmpty() || !provider.defaultModels.contains(modelId)) {
            modelId = provider.defaultModels.firstOrNull() ?: modelId
        }
    }

    // Update LLM defaults when LLM provider changes
    LaunchedEffect(llmProvider) {
        if (llmBaseUrl.isEmpty() || llmBaseUrl == apiSettings.llmConfig.provider.defaultEndpoint) {
            llmBaseUrl = llmProvider.defaultEndpoint
        }
        llmRequiresAuth = llmProvider.requiresAuth

        // Auto-select first model for LLM provider
        if (llmModelId.isEmpty() || !llmProvider.defaultModels.contains(llmModelId)) {
            llmModelId = llmProvider.defaultModels.firstOrNull() ?: llmModelId
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsHamburgerMenu(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(tab.ordinal)
                    }
                },
                onClose = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                selectedTab.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        if (appearanceSettings.techieModeEnabled) {
                            IconButton(onClick = {
                                val exportJson = viewModel.buildSecretsExportJson()
                                val clip = ClipData.newPlainText("hyperwhisper-secrets", exportJson)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    "Secrets exported. Save clipboard as local .env in project root.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Export Secrets",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        // Save and close button
                        IconButton(onClick = {
                            coroutineScope.launch {
                                // Save transcription provider settings
                                viewModel.saveApiSettingsAndWait(
                                    provider, baseUrl, apiKey, requiresAuth, modelId,
                                    inputLanguage, outputLanguage
                                )
                                // Save LLM configuration
                                viewModel.updateLlmConfig(
                                    com.hyperwhisper.data.LlmConfig(
                                        provider = llmProvider,
                                        customBaseUrl = llmBaseUrl,
                                        apiKey = llmApiKey,
                                        requiresAuth = llmRequiresAuth,
                                        modelId = llmModelId
                                    )
                                )
                                (context as? Activity)?.finish()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save and Close",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Close button (without saving)
                        IconButton(onClick = {
                            (context as? Activity)?.finish()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Help/About button
                        IconButton(onClick = {
                            context.startActivity(Intent(context, AboutActivity::class.java))
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondBoundsPageCount = 1
                ) { page ->
                    val tab = SettingsTab.values()[page]
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        item { Divider() }

                        when (tab) {
                            SettingsTab.API_CONFIG -> {
                                item {
                                    ApiConfigSection(
                                        provider = provider,
                                        baseUrl = baseUrl,
                                        apiKey = apiKey,
                                        requiresAuth = requiresAuth,
                                        modelId = modelId,
                                        inputLanguage = inputLanguage,
                                        outputLanguage = outputLanguage,
                                        connectionTestState = connectionTestState,
                                        llmApiKey = llmApiKey,
                                        onProviderChange = { provider = it },
                                        onBaseUrlChange = { baseUrl = it },
                                        onApiKeyChange = { apiKey = it },
                                        onRequiresAuthChange = { requiresAuth = it },
                                        onModelIdChange = { modelId = it },
                                        onInputLanguageChange = { inputLanguage = it },
                                        onOutputLanguageChange = { outputLanguage = it },
                                        onTestConnection = {
                                            viewModel.testConnection(provider, baseUrl, apiKey, modelId)
                                        },
                                        onResetDefaults = {
                                            baseUrl = provider.defaultEndpoint
                                            requiresAuth = provider.requiresAuth
                                            modelId = provider.defaultModels.firstOrNull() ?: modelId
                                        },
                                        onShowModelInfo = { showModelInfo = true },
                                        onShowProviderKeyHelp = { showProviderKeyHelp = true },
                                        onReuseProviderKeyForLlm = {
                                            llmApiKey = apiKey
                                            if (llmProvider == com.hyperwhisper.data.LlmProvider.NONE) {
                                                llmProvider = com.hyperwhisper.data.LlmProvider.OPENAI
                                                llmBaseUrl = com.hyperwhisper.data.LlmProvider.OPENAI.defaultEndpoint
                                                llmRequiresAuth = com.hyperwhisper.data.LlmProvider.OPENAI.requiresAuth
                                                llmModelId = com.hyperwhisper.data.LlmProvider.OPENAI.defaultModels.firstOrNull() ?: llmModelId
                                            }
                                        },
                                        onShowInputLanguageInfo = { showInputLanguageInfo = true },
                                        onShowLogsDialog = { showLogsDialog = true },
                                        onShowApiCallLogs = { showApiCallLogs = true },
                                        onResetConnectionTestState = { viewModel.resetConnectionTestState() }
                                    )
                                }
                            }

                            SettingsTab.LOCAL_MODELS -> {
                                item {
                                    LocalModelsSection(
                                        localSettings = apiSettings.localModelSettings,
                                        discoveredModels = discoveredModels,
                                        onSettingsChange = { viewModel.updateLocalModelSettings(it) },
                                        onDiscoverModels = { viewModel.discoverModels() },
                                        onVerifyModel = { viewModel.verifyModelIntegrity(it) }
                                    )
                                }
                            }

                            SettingsTab.LLM_CONFIG -> {
                                item {
                                    Text(
                                        text = "Configure the LLM used for transforming and translating transcriptions",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item {
                                    com.hyperwhisper.ui.settings.sections.LlmConfigSection(
                                        llmProvider = llmProvider,
                                        llmBaseUrl = llmBaseUrl,
                                        llmApiKey = llmApiKey,
                                        llmRequiresAuth = llmRequiresAuth,
                                        llmModelId = llmModelId,
                                        providerApiKey = apiKey,
                                        onLlmProviderChange = { llmProvider = it },
                                        onLlmBaseUrlChange = { llmBaseUrl = it },
                                        onLlmApiKeyChange = { llmApiKey = it },
                                        onLlmRequiresAuthChange = { llmRequiresAuth = it },
                                        onLlmModelIdChange = { llmModelId = it },
                                        onResetLlmDefaults = {
                                            llmBaseUrl = llmProvider.defaultEndpoint
                                            llmRequiresAuth = llmProvider.requiresAuth
                                            llmModelId = llmProvider.defaultModels.firstOrNull() ?: llmModelId
                                        },
                                        onReuseLlmKeyForProvider = {
                                            apiKey = llmApiKey
                                            if (!requiresAuth) requiresAuth = true
                                        },
                                        onShowLlmInfo = { showLlmInfo = true }
                                    )
                                }
                            }

                            SettingsTab.VOICE_MODES -> {
                                item {
                                    Text(
                                        text = "Customize how your transcriptions are processed and formatted",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item {
                                    VoiceModesSection(
                                        voiceModes = voiceModes,
                                        onAddMode = { showAddModeDialog = true },
                                        onEditMode = { editingMode = it },
                                        onDeleteMode = { viewModel.deleteVoiceMode(it) }
                                    )
                                }
                            }

                            SettingsTab.APPEARANCE -> {
                                item {
                                    Text(
                                        text = "Customize the look and feel of HyperWhisper",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item {
                                    SectionCard(
                                        title = strings.appearanceSettings,
                                        icon = Icons.Default.Palette
                                    ) {
                                        AppearanceSection(
                                            appearanceSettings = appearanceSettings,
                                            onSettingsChange = { viewModel.saveAppearanceSettings(it) }
                                        )
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    tonalElevation = 4.dp
                ) {
                    val currentPage = pagerState.currentPage
                    val totalPages = SettingsTab.values().size
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                if (currentPage < totalPages - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(currentPage + 1)
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentPage < totalPages - 1) "NEXT: ${SettingsTab.values()[currentPage + 1].title}" else "END OF SETTINGS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentPage < totalPages - 1) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showModelInfo) {
        ModelInfoDialog(
            provider = provider,
            modelId = modelId,
            onDismiss = { showModelInfo = false }
        )
    }

    if (showInputLanguageInfo) {
        InputLanguageInfoDialog(
            onDismiss = { showInputLanguageInfo = false }
        )
    }

    if (showProviderKeyHelp) {
        ProviderKeyInstructionsDialog(
            provider = provider,
            onDismiss = { showProviderKeyHelp = false }
        )
    }

    if (showLogsDialog) {
        LogsInfoDialog(
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showLlmInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLlmInfo = false },
            title = { androidx.compose.material3.Text("LLM Post-Processing Info") },
            text = {
                androidx.compose.material3.Text(
                    """
                    Provider: ${llmProvider.displayName}
                    Model: $llmModelId

                    This LLM is used for:
                    • Transforming transcriptions (polite, casual, etc.)
                    • Translating to output language
                    • Applying custom voice mode prompts

                    Not used in verbatim mode.
                    """.trimIndent()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showLlmInfo = false }) {
                    androidx.compose.material3.Text("OK")
                }
            }
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

    if (showApiCallLogs) {
        ApiCallLogsScreen(
            logs = apiCallLogs,
            statistics = apiCallStatistics,
            onClearLogs = {
                coroutineScope.launch {
                    viewModel.clearApiCallLogs()
                }
            },
            onDismiss = { showApiCallLogs = false }
        )
    }
}
