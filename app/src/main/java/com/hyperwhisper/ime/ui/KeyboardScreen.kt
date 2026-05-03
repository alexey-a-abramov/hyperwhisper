package com.hyperwhisper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.hyperwhisper.data.ProcessingPhase
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.data.TranscriptionHistoryItem
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.SUPPORTED_LANGUAGES
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.buttons.InputLanguageButton
import com.hyperwhisper.ui.buttons.OutputLanguageButton
import com.hyperwhisper.ui.buttons.PeriodKeyWithPopup
import com.hyperwhisper.ui.util.repeatOnHold
import com.hyperwhisper.ui.components.InputFieldInfo
import com.hyperwhisper.ui.indicators.RecordingTimer
import com.hyperwhisper.ui.overlays.ConfigInfoDialog
import com.hyperwhisper.ui.overlays.ConfigurationConfirmationDialog
import com.hyperwhisper.ui.overlays.ErrorOverlay
import com.hyperwhisper.ui.panels.ReprocessSettingsDialog
import com.hyperwhisper.ui.panels.TranscriptionHistoryPanel
import com.hyperwhisper.ui.sections.BottomActionsRow
import com.hyperwhisper.ui.sections.LanguageModelRow
import com.hyperwhisper.ui.sections.RecordingSection
import com.hyperwhisper.ui.sections.TopControlsRow
import com.hyperwhisper.ui.dialogs.ModeSelectionDialog
import com.hyperwhisper.ui.dialogs.CancelRecordingConfirmationDialog
import com.hyperwhisper.ui.selectors.LanguageSelectorDialog
import com.hyperwhisper.ui.selectors.ProviderModelSelectorDialog
import com.hyperwhisper.ui.selectors.ModeSelector
import com.hyperwhisper.ui.dialogs.EnterActionSelectorDialog
import com.hyperwhisper.ui.dialogs.EnterAction
import com.hyperwhisper.ui.dialogs.LayoutSelectorDialog
import kotlinx.coroutines.CoroutineScope

private enum class KeyboardActionStyle {
    NORMAL,
    SPACE,
    BACKSPACE,
    ENTER
}

internal val KeyboardSurfaceColor = Color(0xFF000000)
internal val KeyboardKeyColor = Color(0xFFFFFFFF)
internal val KeyboardKeyTextColor = Color(0xFF000000)
// Canonical action-button palette. Same yellow/red/green across every layout
// (QWERTY, Code, Emoji, Dictation's bottom row) so muscle memory transfers.
internal val KeyboardSpaceColor = Color(0xFFFFEB3B)
internal val KeyboardBackspaceColor = Color(0xFFD32F2F)
internal val KeyboardEnterColor = Color(0xFF00C853)
private val KeyboardSpecialTextColor = Color(0xFF000000)
private val KeyboardModeSwitcherColor = Color(0xFF424242)

@Composable
internal fun UnifiedModeSwitcher(
    currentMode: KeyboardInputMode,
    onModeChange: (KeyboardInputMode) -> Unit,
    onReturnToDictation: () -> Unit,
    currentLayout: com.hyperwhisper.data.KeyboardLayout = com.hyperwhisper.data.KeyboardLayout.ENGLISH,
    onLayoutSelectorClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Layout button (shows 2-letter code like EN, RU, etc.)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onLayoutSelectorClick?.invoke()
                        },
                        onTap = {
                            when (currentMode) {
                                KeyboardInputMode.QWERTY -> onModeChange(KeyboardInputMode.SPECIAL_CHARS)
                                KeyboardInputMode.SPECIAL_CHARS -> onModeChange(KeyboardInputMode.QWERTY)
                                else -> onModeChange(KeyboardInputMode.QWERTY)
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                color = if (currentMode == KeyboardInputMode.QWERTY || currentMode == KeyboardInputMode.SPECIAL_CHARS)
                    MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLayout.code,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Dictation button (with mic icon)
        Surface(
            onClick = onReturnToDictation,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.DICTATION)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = strings.keyboardDictationDesc,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Numpad button
        Surface(
            onClick = { onModeChange(KeyboardInputMode.NUMPAD) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.NUMPAD)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "123",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Vibe Coding button
        Surface(
            onClick = { onModeChange(KeyboardInputMode.VIBE_CODING) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.VIBE_CODING)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "</>",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    editorInfo: EditorInfo? = null,
    onTextCommit: (String) -> Unit,
    onDelete: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onSpace: () -> Unit = {},
    onEnter: () -> Unit = {},
    onMoveCursorLeft: () -> Unit = {},
    onMoveCursorRight: () -> Unit = {},
    onMoveCursorUp: () -> Unit = {},
    onMoveCursorDown: () -> Unit = {},
    onPageUp: () -> Unit = {},
    onPageDown: () -> Unit = {},
    onHome: () -> Unit = {},
    onEnd: () -> Unit = {},
    onInsert: () -> Unit = {},
    onForwardDelete: () -> Unit = {},
    onEscape: () -> Unit = {},
    onTab: () -> Unit = {},
    onInsertClipboard: () -> Unit = {},
    onSwitchKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val recordingState by viewModel.recordingState.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val processingInfo by viewModel.processingInfo.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val transcriptionProgress by viewModel.transcriptionProgress.collectAsState()
    val processingStage by viewModel.processingStage.collectAsState()
    val transcriptionHistory by viewModel.transcriptionHistory.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()
    val selectedModeId by viewModel.selectedModeId.collectAsState()
    val apiSettings by viewModel.apiSettings.collectAsState()
    val appearanceSettings by viewModel.appearanceSettings.collectAsState()
    val recentlyUsedLanguages by viewModel.recentlyUsedLanguages.collectAsState()
    val recentlyUsedProviderModels by viewModel.recentlyUsedProviderModels.collectAsState()
    val configuredProviders by viewModel.configuredProviders.collectAsState()
    val usageStatistics by viewModel.usageStatistics.collectAsState()
    val pendingCommandResult by viewModel.pendingCommandResult.collectAsState()
    val lastAudioFileSize by viewModel.lastAudioFileSize.collectAsState()
    val lastAudioDuration by viewModel.lastAudioDuration.collectAsState()
    val walkieTalkieMode by viewModel.walkieTalkieMode.collectAsState()
    val modeChangeMessage by viewModel.modeChangeMessage.collectAsState()
    val showCancelConfirmation by viewModel.showCancelConfirmation.collectAsState()
    val processingPhase by viewModel.processingPhase.collectAsState()

    var showConfigInfo by remember { mutableStateOf(false) }
    var showInputLanguageDialog by remember { mutableStateOf(false) }
    var showOutputLanguageDialog by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showTimerText by remember { mutableStateOf(true) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showProviderModelDialog by remember { mutableStateOf(false) }
    var keyboardInputMode by remember { mutableStateOf(appearanceSettings.lastKeyboardInputMode) }
    var currentKeyboardLayout by remember { mutableStateOf(appearanceSettings.currentKeyboardLayout) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    var showLayoutSelector by remember { mutableStateOf(false) }
    var showEnterActionSelector by remember { mutableStateOf(false) }
    var lastSpacePressTime by remember { mutableStateOf(0L) }
    var spacePressDuration by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    // Track last transcribed text for paste button
    // Initialize from history if available
    var lastTranscribedText by remember { mutableStateOf("") }

    // Initialize lastTranscribedText from history on first load
    LaunchedEffect(transcriptionHistory) {
        if (lastTranscribedText.isEmpty() && transcriptionHistory.isNotEmpty()) {
            lastTranscribedText = transcriptionHistory.first().text
        }
    }

    // Get clipboard manager
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // Save keyboard input mode to settings whenever it changes.
    // Two writes happen here:
    //  1. Global lastKeyboardInputMode — fallback for apps we've never seen.
    //  2. Per-app memory — so reopening the same app restores this layout.
    // The view model gates the per-app write on the master toggle.
    LaunchedEffect(keyboardInputMode) {
        if (keyboardInputMode != appearanceSettings.lastKeyboardInputMode) {
            val updatedSettings = appearanceSettings.copy(lastKeyboardInputMode = keyboardInputMode)
            viewModel.saveKeyboardInputMode(updatedSettings)
        }
        viewModel.recordLayoutForCurrentApp(keyboardInputMode)
    }

    // Apply layout-switch requests from outside the Compose tree (the IME
    // service emits one in onStartInputView when per-app memory has a hit).
    // Normalize against the legacy enum collapse just like the in-line guard
    // below does for stored values.
    LaunchedEffect(viewModel) {
        viewModel.requestedLayout.collect { requested ->
            val normalized = requested.normalize()
            if (normalized != keyboardInputMode) {
                keyboardInputMode = normalized
            }
        }
    }

    val handleSpacePress = {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSpace = currentTime - lastSpacePressTime

        if (timeSinceLastSpace < 500L && lastSpacePressTime > 0L) {
            onDelete()
            onTextCommit(". ")
            lastSpacePressTime = 0L
        } else {
            onSpace()
            lastSpacePressTime = currentTime
        }
    }

    val handleSpaceLongPress = {
        showLayoutSelector = true
    }

    val handleEnterLongPress = {
        showEnterActionSelector = true
    }

    // Auto-commit transcribed text
    LaunchedEffect(transcribedText) {
        if (transcribedText.isNotEmpty()) {
            // Save for paste button
            lastTranscribedText = transcribedText

            // Auto-copy to clipboard if enabled
            if (appearanceSettings.autoCopyToClipboard) {
                val clip = ClipData.newPlainText("Transcribed Text", transcribedText)
                clipboardManager.setPrimaryClip(clip)
            }

            onTextCommit(transcribedText)
            viewModel.clearTranscribedText()
        }
    }

    // Show walkie-talkie mode change message
    LaunchedEffect(modeChangeMessage) {
        modeChangeMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearModeChangeMessage()
        }
    }

    // Show processing info as Toast
    LaunchedEffect(processingInfo) {
        processingInfo?.let { info ->
            // Build toast message
            val message = buildString {
                append("✓ ${info.processingMode.uppercase()}")
                if (info.translationEnabled && info.translationTarget != null) {
                    append(" • Translated to ${info.translationTarget}")
                }
                append("\n")
                if (info.processingMode == "two-step") {
                    append("1️⃣ ${info.transcriptionModel}")
                    if (info.originalTranscription != null && info.originalTranscription.length <= 50) {
                        append(" → \"${info.originalTranscription}\"")
                    }
                    append("\n2️⃣ ${info.postProcessingModel ?: "unknown"}")
                } else {
                    append("${info.transcriptionModel} (${info.strategy})")
                }
            }

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // Auto-dismiss after showing Toast
            delay(5000)
            viewModel.clearProcessingInfo()
        }
    }

    // DON'T auto-clear errors - let user read them
    // Errors will be cleared when user taps mic again or manually dismisses

    // Normalize legacy persisted modes so the user always lands on one of
    // the 4 supported layouts — even if their saved-last-mode was NUMPAD or
    // similar from a pre-consolidation build.
    LaunchedEffect(Unit) {
        val normalized = keyboardInputMode.normalize()
        if (normalized != keyboardInputMode) keyboardInputMode = normalized
    }

    // Mode cycle = the 4 base modes plus any agent modes the user has enabled.
    val keyboardModeOrder = remember(appearanceSettings.enabledAgentKeyboards) {
        val base = listOf(
            com.hyperwhisper.data.KeyboardInputMode.DICTATION,
            com.hyperwhisper.data.KeyboardInputMode.QWERTY,
            com.hyperwhisper.data.KeyboardInputMode.CODE,
            com.hyperwhisper.data.KeyboardInputMode.EMOJI
        )
        val agents = com.hyperwhisper.data.KeyboardInputMode.agentModes
            .filter { it.name in appearanceSettings.enabledAgentKeyboards }
        base + agents
    }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var modeChangeToast by remember { mutableStateOf<com.hyperwhisper.data.KeyboardInputMode?>(null) }
    LaunchedEffect(modeChangeToast) {
        if (modeChangeToast != null) {
            kotlinx.coroutines.delay(1100)
            modeChangeToast = null
        }
    }
    val cycleKeyboardMode: (Int) -> Unit = { dir ->
        val cur = keyboardModeOrder.indexOf(keyboardInputMode).coerceAtLeast(0)
        val next = keyboardModeOrder[(cur + dir + keyboardModeOrder.size) % keyboardModeOrder.size]
        keyboardInputMode = next
        modeChangeToast = next
    }
    val swipeAccum = remember { kotlin.collections.mutableListOf(0f) }
    val swipeDensity = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier = modifier.fillMaxWidth().height(320.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeAccum[0] = 0f },
                        onDragEnd = {
                            // Threshold ~64dp = a deliberate swipe, not a stray drag.
                            val px = with(swipeDensity) { 64.dp.toPx() }
                            val dx = swipeAccum[0]
                            if (kotlin.math.abs(dx) > px) {
                                if (dx < 0) cycleKeyboardMode(1)
                                else cycleKeyboardMode(-1)
                            }
                            swipeAccum[0] = 0f
                        },
                        onDragCancel = { swipeAccum[0] = 0f },
                        onHorizontalDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, dx: Float ->
                            swipeAccum[0] = swipeAccum[0] + dx
                        }
                    )
                },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        if (keyboardInputMode == KeyboardInputMode.DICTATION) 16.dp else 0.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
            if (keyboardInputMode == KeyboardInputMode.DICTATION) {
                // Single universal navigation row — replaces the old top-controls
                // row entirely. Settings/help/logs live in the strip itself,
                // so we don't waste a second row.
                UniversalKeyboardTopStrip(
                    currentMode = keyboardInputMode,
                    cycleOrder = keyboardModeOrder,
                    onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
                    onReturnToVoice = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onEsc = onEscape,
                    onTab = onTab,
                    onBackspace = onDelete,
                    onSettings = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.settings.SettingsActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    },
                    onLogs = if (appearanceSettings.techieModeEnabled) {
                        {
                            val intent = android.content.Intent(
                                context, com.hyperwhisper.ui.logs.LogsActivity::class.java
                            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        }
                    } else null,
                    onHelp = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.about.AboutActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (keyboardInputMode == KeyboardInputMode.DICTATION) {
                // Language & Model Info Row (only in dictation mode)
                LanguageModelRow(
                    apiSettings = apiSettings,
                    recordingState = recordingState,
                    techieModeEnabled = appearanceSettings.techieModeEnabled,
                    voiceModes = voiceModes,
                    selectedModeId = selectedModeId,
                    onShowInputLanguageDialog = { showInputLanguageDialog = true },
                    onShowOutputLanguageDialog = { showOutputLanguageDialog = true },
                    onShowConfigInfo = { showConfigInfo = true },
                    onShowProviderModelDialog = { showProviderModelDialog = true },
                    onShowModeDialog = { showModeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

            if (keyboardInputMode == KeyboardInputMode.DICTATION) {
                // Middle section: Cancel (far left) + Mic (center) + Enter (right)
                RecordingSection(
                    recordingState = recordingState,
                    recordingDuration = recordingDuration,
                    transcriptionProgress = transcriptionProgress,
                    processingStage = processingStage,
                    processingPhase = processingPhase,
                    lastAudioFileSize = lastAudioFileSize,
                    lastAudioDuration = lastAudioDuration,
                    editorInfo = editorInfo,
                    techieModeEnabled = appearanceSettings.techieModeEnabled,
                    showTimerText = showTimerText,
                    walkieTalkieMode = walkieTalkieMode,
                    onCancelRecording = { viewModel.cancelRecording() },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    onCancelTranscription = { viewModel.cancelTranscription() },
                    onEnableWalkieTalkieMode = { viewModel.enableWalkieTalkieMode() },
                    onDisableWalkieTalkieMode = { viewModel.disableWalkieTalkieMode() },
                    onPressStartRecording = { viewModel.startRecording() },
                    onPressReleaseRecording = { viewModel.stopRecording() },
                    onConfirmRecording = { viewModel.confirmRecording() },
                    onToggleTimer = { showTimerText = !showTimerText },
                    onEnter = onEnter,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                BottomActionsRow(
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPasteText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    onSpace = handleSpacePress,
                    // Mode switching now lives in the universal top strip,
                    // so suppress the redundant in-row switcher.
                    showKeyboardButton = false,
                    onKeyboardButtonClick = { keyboardInputMode = KeyboardInputMode.QWERTY },
                    currentKeyboardMode = keyboardInputMode,
                    onModeChange = { keyboardInputMode = it }
                )
            } else if (keyboardInputMode.isAgent) {
                UniversalKeyboardTopStrip(
                    currentMode = keyboardInputMode,
                    cycleOrder = keyboardModeOrder,
                    onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
                    onReturnToVoice = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onModePillTap = { modeMenuExpanded = true },
                    onEsc = onEscape,
                    onTab = onTab,
                    onBackspace = onDelete,
                    onSettings = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.settings.SettingsActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    },
                    onHelp = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.about.AboutActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                )
                AgentKeyboard(
                    title = keyboardInputMode.displayName,
                    commands = com.hyperwhisper.data.AgentCommands.byMode(keyboardInputMode),
                    onInsert = onTextCommit,
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    onDelete = onDelete,
                    modifier = Modifier.weight(1f)
                )
            } else if (keyboardInputMode == KeyboardInputMode.EMOJI) {
                UniversalKeyboardTopStrip(
                    currentMode = keyboardInputMode,
                    cycleOrder = keyboardModeOrder,
                    onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
                    onReturnToVoice = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onModePillTap = { modeMenuExpanded = true },
                    onEsc = onEscape,
                    onTab = onTab,
                    onBackspace = onDelete,
                    onSettings = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.settings.SettingsActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    },
                    onHelp = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.about.AboutActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                )
                EmojiKeyboard(
                    recentEmojis = appearanceSettings.recentEmojis,
                    searchQuery = emojiSearchQuery,
                    onSearchQueryChange = { emojiSearchQuery = it },
                    onEmojiSelected = { emoji ->
                        onTextCommit(emoji)
                        // Update recent emojis
                        val updatedRecents = (listOf(emoji) + appearanceSettings.recentEmojis)
                            .distinct()
                            .take(10)
                        val updatedSettings = appearanceSettings.copy(recentEmojis = updatedRecents)
                        viewModel.updateRecentEmojis(updatedSettings)
                    },
                    onBackspace = onDelete,
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    onReturnToDictation = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onModeChange = { keyboardInputMode = it },
                    currentMode = keyboardInputMode,
                    modifier = Modifier.weight(1f)
                )
            } else {
                UniversalKeyboardTopStrip(
                    currentMode = keyboardInputMode,
                    cycleOrder = keyboardModeOrder,
                    onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
                    onReturnToVoice = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onModePillTap = { modeMenuExpanded = true },
                    onEsc = onEscape,
                    onTab = onTab,
                    onBackspace = onDelete,
                    onSettings = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.settings.SettingsActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    },
                    onHelp = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.about.AboutActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                )
                val codeModifierState by viewModel.modifierKeyState.state.collectAsState()
                TextKeyboardSectionNew(
                    mode = keyboardInputMode,
                    layout = currentKeyboardLayout,
                    recordingState = recordingState,
                    recordingDuration = recordingDuration,
                    onModeChange = { keyboardInputMode = it },
                    onKeyPress = onTextCommit,
                    onSpacePress = handleSpacePress,
                    onSpaceLongPress = handleSpaceLongPress,
                    onEnterLongPress = handleEnterLongPress,
                    onDelete = onDelete,
                    onEnter = onEnter,
                    onMoveCursorLeft = onMoveCursorLeft,
                    onMoveCursorRight = onMoveCursorRight,
                    onMoveCursorUp = onMoveCursorUp,
                    onMoveCursorDown = onMoveCursorDown,
                    onPageUp = onPageUp,
                    onPageDown = onPageDown,
                    onHome = onHome,
                    onEnd = onEnd,
                    onInsert = onInsert,
                    onForwardDelete = onForwardDelete,
                    onEscape = onEscape,
                    onTab = onTab,
                    onReturnToDictation = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
                    modifierState = codeModifierState,
                    onToggleCtrl = { viewModel.modifierKeyState.toggleCtrl() },
                    onToggleAlt = { viewModel.modifierKeyState.toggleAlt() },
                    onToggleShift = { viewModel.modifierKeyState.toggleShift() },
                    onLockCtrl = { viewModel.modifierKeyState.lockCtrl() },
                    onLockAlt = { viewModel.modifierKeyState.lockAlt() },
                    onLockShift = { viewModel.modifierKeyState.lockShift() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        }

        // Show Error Overlay when there's an error (as overlay within keyboard)
        errorMessage?.let { error ->
            ErrorOverlay(
                errorMessage = error,
                onDismiss = { viewModel.clearError() },
                context = context
            )
        }

        // Show Configuration Command Confirmation Dialog
        pendingCommandResult?.let { result ->
            ConfigurationConfirmationDialog(
                settingChanged = result.settingChanged,
                newValue = result.newValue,
                message = result.message,
                onConfirm = { viewModel.confirmPendingCommand() },
                onDismiss = { viewModel.rejectPendingCommand() }
            )
        }

        // Confirmation dialog removed - all recordings now process automatically
        // Confirmation only shown when CANCELING a long recording (see below)

        // Show Cancel Confirmation Dialog (when canceling long recordings)
        if (showCancelConfirmation) {
            CancelRecordingConfirmationDialog(
                durationSeconds = recordingDuration / 1000,
                onConfirm = { viewModel.confirmCancelRecording() },
                onDismiss = { viewModel.dismissCancelConfirmation() }
            )
        }

        // Show Config Info Dialog
        if (showConfigInfo) {
            ConfigInfoDialog(
                apiSettings = apiSettings,
                usageStatistics = usageStatistics,
                onDismiss = { showConfigInfo = false }
            )
        }

        // Show Mode Selection Dialog
        if (showModeDialog) {
            ModeSelectionDialog(
                currentMode = voiceModes.firstOrNull { it.id == selectedModeId },
                allModes = voiceModes,
                onModeSelected = { modeId ->
                    viewModel.selectMode(modeId)
                    showModeDialog = false
                },
                onDismiss = { showModeDialog = false }
            )
        }

        // Show Input Language Dialog
        if (showInputLanguageDialog) {
            LanguageSelectorDialog(
                title = "Input Language (Speech)",
                currentLanguage = apiSettings.inputLanguage,
                recentlyUsedLanguages = recentlyUsedLanguages,
                onLanguageSelected = { languageCode ->
                    viewModel.setInputLanguage(languageCode)
                    showInputLanguageDialog = false
                },
                onDismiss = { showInputLanguageDialog = false }
            )
        }

        // Show Provider + Model Dialog
        if (showProviderModelDialog) {
            ProviderModelSelectorDialog(
                currentProvider = apiSettings.provider,
                currentModelId = apiSettings.modelId,
                configuredProviders = configuredProviders,
                recentSelections = recentlyUsedProviderModels,
                onProviderModelSelected = { provider, modelId ->
                    viewModel.setProviderAndModel(provider, modelId)
                    showProviderModelDialog = false
                },
                onDismiss = { showProviderModelDialog = false }
            )
        }

        // Show Output Language Dialog
        if (showOutputLanguageDialog) {
            LanguageSelectorDialog(
                title = "Output Language (Translation)",
                currentLanguage = apiSettings.outputLanguage,
                recentlyUsedLanguages = recentlyUsedLanguages,
                onLanguageSelected = { languageCode ->
                    viewModel.setOutputLanguage(languageCode)
                    showOutputLanguageDialog = false
                },
                onDismiss = { showOutputLanguageDialog = false }
            )
        }

        // Show Layout Selector Dialog
        if (showLayoutSelector) {
            LayoutSelectorDialog(
                currentLayout = currentKeyboardLayout,
                currentMode = keyboardInputMode,
                enabledLayouts = appearanceSettings.enabledKeyboardLayouts,
                currentInputLanguage = apiSettings.inputLanguage,
                currentOutputLanguage = apiSettings.outputLanguage,
                currentVoiceMode = voiceModes.firstOrNull { it.id == selectedModeId },
                onLayoutSelected = { layout ->
                    currentKeyboardLayout = layout
                    val updatedSettings = appearanceSettings.copy(currentKeyboardLayout = layout)
                    viewModel.updateKeyboardLayout(updatedSettings)
                },
                onModeSelected = { mode ->
                    keyboardInputMode = mode
                },
                onShowInputLanguageDialog = {
                    showLayoutSelector = false
                    showInputLanguageDialog = true
                },
                onShowOutputLanguageDialog = {
                    showLayoutSelector = false
                    showOutputLanguageDialog = true
                },
                onShowVoiceModeDialog = {
                    showLayoutSelector = false
                    showModeDialog = true
                },
                onDismiss = { showLayoutSelector = false }
            )
        }

        // Show Enter Action Selector Dialog
        if (showEnterActionSelector) {
            EnterActionSelectorDialog(
                editorInfo = editorInfo,
                onActionSelected = { action ->
                    when (action) {
                        EnterAction.NEWLINE -> onTextCommit("\n")
                        EnterAction.SUBMIT -> onEnter()
                        EnterAction.LINE_BREAK -> onTextCommit("\n")
                    }
                    showEnterActionSelector = false
                },
                onDismiss = { showEnterActionSelector = false }
            )
        }

        // Show History Panel
        if (showHistoryPanel) {
            var selectedItemForReprocess by remember { mutableStateOf<TranscriptionHistoryItem?>(null) }

            TranscriptionHistoryPanel(
                history = transcriptionHistory,
                onSelect = { text ->
                    onTextCommit(text)
                    showHistoryPanel = false
                },
                onClearAll = { viewModel.clearHistory() },
                onDismiss = { showHistoryPanel = false },
                onPlayAudio = { item ->
                    val audioPath = item.audioFilePath
                    if (audioPath.isNullOrBlank()) {
                        Toast.makeText(context, "No saved audio for this entry", Toast.LENGTH_SHORT).show()
                    } else {
                        val audioFile = java.io.File(audioPath)
                        if (!audioFile.exists()) {
                            Toast.makeText(context, "Saved audio file not found", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                val mediaPlayer = MediaPlayer().apply {
                                    setDataSource(audioPath)
                                    setOnPreparedListener { it.start() }
                                    setOnCompletionListener { it.release() }
                                    setOnErrorListener { mp, _, _ ->
                                        mp.release()
                                        true
                                    }
                                    prepareAsync()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to play audio", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onReprocessWithCurrentSettings = { item ->
                    viewModel.reprocessWithCurrentSettings(item)
                    showHistoryPanel = false
                },
                onReprocessWithNewSettings = { item ->
                    selectedItemForReprocess = item
                }
            )

            // Dialog for selecting new settings for reprocessing
            selectedItemForReprocess?.let { item ->
                ReprocessSettingsDialog(
                    item = item,
                    currentApiSettings = apiSettings,
                    currentVoiceModes = voiceModes,
                    currentSelectedModeId = selectedModeId,
                    onConfirm = { newSettings, newMode ->
                        viewModel.reprocessWithNewSettings(item, newSettings, newMode)
                        selectedItemForReprocess = null
                        showHistoryPanel = false
                    },
                    onDismiss = {
                        selectedItemForReprocess = null
                    }
                )
            }
        }

        // Mode-picker dropdown overlay. Compose Popup/DropdownMenu can't be
        // used in IMEs (BadTokenException — same root cause as Compose
        // Dialog), so we render the menu inline as a Card positioned below
        // the strip + a tap-to-dismiss scrim covering the rest of the surface.
        if (modeMenuExpanded) {
            val scrimSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = scrimSource
                    ) { modeMenuExpanded = false }
            )
            Card(
                modifier = Modifier
                    .padding(top = 38.dp, start = 60.dp)
                    .widthIn(min = 180.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    keyboardModeOrder.forEach { mode ->
                        val isCurrent = mode == keyboardInputMode.normalize()
                        Surface(
                            onClick = {
                                keyboardInputMode = mode
                                modeChangeToast = mode
                                modeMenuExpanded = false
                            },
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(22.dp))
                                }
                                Text(
                                    text = mode.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                    color = if (isCurrent)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode-change toast: appears when swipe cycles to a new keyboard mode.
        // Auto-dismisses via the LaunchedEffect at the top of the function.
        androidx.compose.animation.AnimatedVisibility(
            visible = modeChangeToast != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            modeChangeToast?.let { mode ->
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = mode.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextKeyboardSectionNew(
    mode: KeyboardInputMode,
    layout: com.hyperwhisper.data.KeyboardLayout = com.hyperwhisper.data.KeyboardLayout.ENGLISH,
    recordingState: RecordingState = RecordingState.IDLE,
    recordingDuration: Long = 0L,
    onModeChange: (KeyboardInputMode) -> Unit,
    onKeyPress: (String) -> Unit,
    onSpacePress: () -> Unit,
    onSpaceLongPress: () -> Unit = {},
    onEnterLongPress: () -> Unit = {},
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onMoveCursorUp: () -> Unit,
    onMoveCursorDown: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onHome: () -> Unit,
    onEnd: () -> Unit,
    onInsert: () -> Unit = {},
    onForwardDelete: () -> Unit = {},
    onEscape: () -> Unit = {},
    onTab: () -> Unit = {},
    onReturnToDictation: () -> Unit,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    modifierState: com.hyperwhisper.ime.keyboard.ModifierKeyState.State =
        com.hyperwhisper.ime.keyboard.ModifierKeyState.State(),
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onToggleShift: () -> Unit = {},
    onLockCtrl: () -> Unit = {},
    onLockAlt: () -> Unit = {},
    onLockShift: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var shiftEnabled by remember { mutableStateOf(false) }
    var capsLockEnabled by remember { mutableStateOf(false) }
    var ctrlSticky by remember { mutableStateOf(false) }
    var altSticky by remember { mutableStateOf(false) }
    var shiftSticky by remember { mutableStateOf(false) }

    val letterCase: (String) -> String = { key ->
        when {
            capsLockEnabled -> key.uppercase()
            shiftEnabled && key.all { it.isLetter() } -> key.uppercase()
            else -> key
        }
    }

    val isSpecialChars = mode == KeyboardInputMode.SPECIAL_CHARS

    // Get the layout definition
    val layoutDef = KeyboardLayouts.getLayout(layout)

    val topRows = if (isSpecialChars) {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("[", "]", "{", "}", "(", ")", "<", ">", "/", "\\"),
            listOf("+", "-", "*", "=", "==", "!=", "&", "|", "&&", "||"),
            listOf("%", "^", "~", "`", ":", ";", "\"", "'", "?", ".")
        )
    } else {
        listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            layoutDef.topRow,
            layoutDef.middleRow
        )
    }

    val bottomRowKeys = if (isSpecialChars) emptyList() else layoutDef.bottomRow

    when (mode.normalize()) {
        KeyboardInputMode.CODE -> {
            CodeKeyboard(
                onKeyPress = onKeyPress,
                onSpacePress = onSpacePress,
                onDelete = onDelete,
                onEnter = onEnter,
                onTab = onTab,
                onEscape = onEscape,
                onMoveCursorLeft = onMoveCursorLeft,
                onMoveCursorRight = onMoveCursorRight,
                onMoveCursorUp = onMoveCursorUp,
                onMoveCursorDown = onMoveCursorDown,
                onHome = onHome,
                onEnd = onEnd,
                onPageUp = onPageUp,
                onPageDown = onPageDown,
                modifierState = modifierState,
                onToggleCtrl = onToggleCtrl,
                onToggleAlt = onToggleAlt,
                onToggleShift = onToggleShift,
                onLockCtrl = onLockCtrl,
                onLockAlt = onLockAlt,
                onLockShift = onLockShift,
                modifier = modifier
            )
        }
        KeyboardInputMode.NUMPAD -> {
            // Classic numpad layout with F-keys and sticky modifiers
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // F-keys row 1 (F1-F6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..6).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = {
                                    // Send F-key escape sequences
                                    val escapeSeq = when(num) {
                                        1 -> "\u001BOP"
                                        2 -> "\u001BOQ"
                                        3 -> "\u001BOR"
                                        4 -> "\u001BOS"
                                        5 -> "\u001B[15~"
                                        6 -> "\u001B[17~"
                                        else -> ""
                                    }
                                    onKeyPress(escapeSeq)
                                },
                                modifier = Modifier.weight(1f),
                                height = 32.dp
                            )
                        }
                    }

                    // F-keys row 2 (F7-F12)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (7..12).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = {
                                    val escapeSeq = when(num) {
                                        7 -> "\u001B[18~"
                                        8 -> "\u001B[19~"
                                        9 -> "\u001B[20~"
                                        10 -> "\u001B[21~"
                                        11 -> "\u001B[23~"
                                        12 -> "\u001B[24~"
                                        else -> ""
                                    }
                                    onKeyPress(escapeSeq)
                                },
                                modifier = Modifier.weight(1f),
                                height = 32.dp
                            )
                        }
                    }

                    // Numpad row 1: Esc / * -
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "Esc",
                            onClick = onEscape,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "/",
                            onClick = {
                                val key = if (shiftSticky || altSticky || ctrlSticky) "\\" else "/"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "*",
                            onClick = {
                                val key = if (shiftSticky) "×" else if (altSticky) "·" else "*"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                        KeyboardKeyButton(
                            label = "-",
                            onClick = {
                                val key = if (shiftSticky) "_" else if (altSticky) "–" else "-"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 40.dp
                        )
                    }

                    // Numpad row 2: 7(Home) 8(↑) 9(PgUp) +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "7\nHome",
                            onClick = {
                                if (altSticky || ctrlSticky) onHome()
                                else onKeyPress("7")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "8\n↑",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorUp()
                                else onKeyPress("8")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "9\nPgUp",
                            onClick = {
                                if (altSticky || ctrlSticky) onPageUp()
                                else onKeyPress("9")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "+",
                            onClick = {
                                val key = if (shiftSticky) "≈" else if (altSticky) "±" else "+"
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                    }

                    // Numpad row 3: 4(←) 5 6(→) =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "4\n←",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorLeft()
                                else onKeyPress("4")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "5",
                            onClick = {
                                onKeyPress("5")
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "6\n→",
                            onClick = {
                                if (altSticky || ctrlSticky) onMoveCursorRight()
                                else onKeyPress("6")
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                        KeyboardKeyButton(
                            label = "=",
                            onClick = {
                                val key = if (shiftSticky) "≠" else if (altSticky) "≡" else "="
                                onKeyPress(key)
                                shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                            },
                            modifier = Modifier.weight(1f),
                            height = 45.dp
                        )
                    }

                    // Numpad row 4: 1(End) 2(↓) 3(PgDn) Enter (tall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(3f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                KeyboardKeyButton(
                                    label = "1\nEnd",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onEnd()
                                        else onKeyPress("1")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = "2\n↓",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onMoveCursorDown()
                                        else onKeyPress("2")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = "3\nPgDn",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onPageDown()
                                        else onKeyPress("3")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                            }

                            // Numpad row 5: 0 . Del
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                KeyboardKeyButton(
                                    label = "0\nIns",
                                    onClick = {
                                        if (altSticky || ctrlSticky) onInsert()
                                        else onKeyPress("0")
                                        altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardKeyButton(
                                    label = ".",
                                    onClick = {
                                        val key = if (shiftSticky) "," else "."
                                        onKeyPress(key)
                                        shiftSticky = false
                                altSticky = false
                                ctrlSticky = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 45.dp
                                )
                                KeyboardActionButton(
                                    label = "Del",
                                    onClick = onForwardDelete,
                                    modifier = Modifier.weight(1f),
                                    style = KeyboardActionStyle.BACKSPACE,
                                    height = 45.dp
                                )
                            }
                        }

                        // Enter button (spans 2 rows on the right)
                        KeyboardActionButton(
                            label = "Enter",
                            onClick = onEnter,
                            modifier = Modifier.weight(1f).height(92.dp),
                            style = KeyboardActionStyle.ENTER
                        )
                    }

                    // Sticky modifiers row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Ctrl (sticky)
                        Surface(
                            onClick = { ctrlSticky = !ctrlSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (ctrlSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ctrl",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ctrlSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Alt (sticky)
                        Surface(
                            onClick = { altSticky = !altSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (altSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Alt",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (altSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Shift (sticky)
                        Surface(
                            onClick = { shiftSticky = !shiftSticky },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (shiftSticky) MaterialTheme.colorScheme.primary else KeyboardKeyColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Shift",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (shiftSticky) Color.White else KeyboardKeyTextColor
                                )
                            }
                        }

                        // Tab
                        KeyboardActionButton(
                            label = "Tab",
                            onClick = onTab,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                    }

                    // Bottom row with mode switcher, space, and backspace
                    Row(
                        modifier = Modifier.fillMaxWidth().height(45.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(2.5f).fillMaxHeight()
                        )

                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.weight(2f),
                            style = KeyboardActionStyle.SPACE
                        )

                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            style = KeyboardActionStyle.BACKSPACE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        KeyboardInputMode.SYSTEM_KEYS -> {
            // System keys layout
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // F-keys row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..12).forEach { num ->
                            KeyboardKeyButton(
                                label = "F$num",
                                onClick = { onKeyPress("\u001B[$num~") },
                                modifier = Modifier.weight(1f),
                                height = 40.dp
                            )
                        }
                    }

                    // Navigation row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "HOME",
                            onClick = onHome,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "END",
                            onClick = onEnd,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "PG↑",
                            onClick = onPageUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            label = "PG↓",
                            onClick = onPageDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                    }

                    // Cursor keys
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Spacer(Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = onMoveCursorUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        Spacer(Modifier.weight(2f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            onClick = onMoveCursorLeft,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = onMoveCursorDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            onClick = onMoveCursorRight,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = 40.dp
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    Spacer(Modifier.weight(1f))

                    // Bottom row with unified mode switcher
                    Row(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher (left)
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(3f).fillMaxHeight()
                        )

                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.weight(2.5f),
                            style = KeyboardActionStyle.SPACE
                        )
                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            style = KeyboardActionStyle.BACKSPACE,
                            modifier = Modifier.weight(1f)
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardReturn,
                            onClick = onEnter,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.ENTER
                        )
                    }
                }
            }
        }
        KeyboardInputMode.VIBE_CODING -> {
            // Vibe Coding mode - programmer's keyboard
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val keyHeight = 36.dp

                    // Row 1: Common brackets and symbols
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("{", "}", "[", "]", "(", ")", "<", ">", "/", "\\").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 2: Special operators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("&", "|", "^", "~", "!", "?", ":", ";", "=", "_").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 3: More symbols
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("@", "#", "$", "%", "*", "+", "-", ".", ",", "\"").forEach { key ->
                            KeyboardKeyButton(
                                label = key,
                                onClick = { onKeyPress(key) },
                                modifier = Modifier.weight(1f),
                                height = keyHeight
                            )
                        }
                    }

                    // Row 4: Navigation cluster with reorganized cursor/backspace/enter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "HOME",
                            onClick = onHome,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "END",
                            onClick = onEnd,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "PG↑",
                            onClick = onPageUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            label = "PG↓",
                            onClick = onPageDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = onMoveCursorUp,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(0.5f))
                        RepeatingActionButton(
                            icon = Icons.Default.Backspace,
                            onAction = onDelete,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.BACKSPACE,
                            height = keyHeight
                        )
                    }

                    // Row 5: Tab, quotes, cursor controls (down positioned below up)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardKeyButton(
                            label = "Tab",
                            onClick = { onKeyPress("\t") },
                            modifier = Modifier.weight(1.5f),
                            height = keyHeight
                        )
                        KeyboardKeyButton(
                            label = "'",
                            onClick = { onKeyPress("'") },
                            modifier = Modifier.weight(1f),
                            height = keyHeight
                        )
                        KeyboardKeyButton(
                            label = "`",
                            onClick = { onKeyPress("`") },
                            modifier = Modifier.weight(1f),
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            onClick = onMoveCursorLeft,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = onMoveCursorDown,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardArrowRight,
                            onClick = onMoveCursorRight,
                            modifier = Modifier.weight(1f),
                            style = KeyboardActionStyle.NORMAL,
                            height = keyHeight
                        )
                        Spacer(modifier = Modifier.weight(0.5f))
                        KeyboardActionButton(
                            icon = Icons.Default.KeyboardReturn,
                            onClick = onEnter,
                            modifier = Modifier.weight(1.2f),
                            style = KeyboardActionStyle.ENTER,
                            height = keyHeight
                        )
                    }

                    // Row 6: Recording controls (cancel + mic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Unified mode switcher (left)
                        UnifiedModeSwitcher(
                            currentMode = mode,
                            onModeChange = onModeChange,
                            onReturnToDictation = onReturnToDictation,
                            modifier = Modifier.weight(2.5f).height(40.dp)
                        )

                        Spacer(modifier = Modifier.weight(3f))

                        // Cancel recording button (only shown when recording)
                        if (recordingState == RecordingState.RECORDING ||
                            recordingState == RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION) {
                            KeyboardActionButton(
                                icon = Icons.Default.Close,
                                onClick = { /* Cancel recording */ },
                                modifier = Modifier.weight(1f),
                                style = KeyboardActionStyle.NORMAL,
                                height = 40.dp
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Recording button for voice input in coding mode
                        Surface(
                            onClick = {
                                when (recordingState) {
                                    RecordingState.IDLE, RecordingState.ERROR -> onStartRecording()
                                    RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> onStopRecording()
                                    else -> {}
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = when (recordingState) {
                                RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION ->
                                    Color(0xFFE53935) // Red when recording
                                RecordingState.PROCESSING -> MaterialTheme.colorScheme.tertiary
                                else -> Color(0xFF4CAF50) // Green when idle
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (recordingState) {
                                    RecordingState.RECORDING, RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> {
                                        // Show timer when recording
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = strings.stopRecording,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            val seconds = (recordingDuration / 1000) % 60
                                            val minutes = (recordingDuration / 1000) / 60
                                            Text(
                                                text = "$minutes:${seconds.toString().padStart(2, '0')}",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    RecordingState.PROCESSING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = strings.startRecording,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Bottom row: Space bar
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        KeyboardActionButton(
                            label = "space",
                            onClick = onSpacePress,
                            modifier = Modifier.fillMaxWidth(),
                            style = KeyboardActionStyle.SPACE
                        )
                    }
                }
            }
        }
        else -> {
            // QWERTY and SPECIAL_CHARS layouts
            Surface(
                modifier = modifier.fillMaxWidth().fillMaxHeight(),
                color = KeyboardSurfaceColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val horizontalGap = 2.dp
                    val verticalGap = 4.dp
                    // Calculate total rows: topRows + shiftRow + bottomRow
                    val totalRows = if (isSpecialChars) {
                        topRows.size + 1 + 1 // topRows + symbols row + bottom row
                    } else {
                        topRows.size + 1 + 1 // topRows + shift row + bottom row
                    }
                    val totalVerticalGaps = verticalGap * (totalRows + 1)
                    val availableHeight = maxHeight - totalVerticalGaps
                    val keyHeight = (availableHeight / totalRows).coerceIn(32.dp, 48.dp)

                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(verticalGap)
                    ) {
                        // Number row
                        topRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    if (key.isEmpty()) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        // Number-row long-press → shifted symbol.
                                        val altSymbol: String? = if (!isSpecialChars) when (key) {
                                            "1" -> "!"
                                            "2" -> "@"
                                            "3" -> "#"
                                            "4" -> "$"
                                            "5" -> "%"
                                            "6" -> "^"
                                            "7" -> "&"
                                            "8" -> "*"
                                            "9" -> "("
                                            "0" -> ")"
                                            else -> null
                                        } else null
                                        KeyboardKeyButton(
                                            label = if (isSpecialChars) key else letterCase(key),
                                            onClick = {
                                                val out = if (isSpecialChars) key else letterCase(key)
                                                onKeyPress(out)
                                                if (shiftEnabled && !isSpecialChars && key.all { it.isLetter() }) {
                                                    shiftEnabled = false
                                                }
                                            },
                                            longPressLabel = altSymbol,
                                            onLongPress = altSymbol?.let { sym -> { onKeyPress(sym) } },
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    }
                                }
                            }
                        }

                        if (isSpecialChars) {
                            // Special characters row
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(",", "_", "@", "#", "$", "€", "£", "¥", "§", "⌫").forEach { key ->
                                    if (key == "⌫") {
                                        RepeatingActionButton(
                                            icon = Icons.Default.Backspace,
                                            onAction = onDelete,
                                            style = KeyboardActionStyle.BACKSPACE,
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    } else {
                                        KeyboardKeyButton(
                                            label = key,
                                            onClick = { onKeyPress(key) },
                                            modifier = Modifier.weight(1f),
                                            height = keyHeight
                                        )
                                    }
                                }
                            }
                        } else {
                            // Shift row (bottom row keys with shift and backspace)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(keyHeight),
                                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KeyboardActionButton(
                                    icon = if (capsLockEnabled) Icons.Default.KeyboardCapslock else Icons.Default.ArrowUpward,
                                    onClick = {
                                        if (shiftEnabled) {
                                            capsLockEnabled = true
                                            shiftEnabled = false
                                        } else if (capsLockEnabled) {
                                            capsLockEnabled = false
                                        } else {
                                            shiftEnabled = true
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    style = KeyboardActionStyle.NORMAL,
                                    height = keyHeight
                                )
                                bottomRowKeys.forEach { key ->
                                    KeyboardKeyButton(
                                        label = letterCase(key),
                                        onClick = {
                                            onKeyPress(letterCase(key))
                                            if (shiftEnabled && !capsLockEnabled) shiftEnabled = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        height = keyHeight
                                    )
                                }
                                RepeatingActionButton(
                                    icon = Icons.Default.Backspace,
                                    onAction = onDelete,
                                    modifier = Modifier.weight(1.5f),
                                    style = KeyboardActionStyle.BACKSPACE,
                                    height = keyHeight
                                )
                            }
                        }

                        // Bottom row — mode switching is in the universal
                        // top strip; bottom row is now pure typing keys.
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Comma
                            KeyboardKeyButton(
                                label = ",",
                                onClick = { onKeyPress(",") },
                                modifier = Modifier.weight(0.7f),
                                height = keyHeight
                            )
                            // Space bar (long-press for layout selector)
                            LongPressActionButton(
                                label = "space",
                                onClick = onSpacePress,
                                onLongPress = onSpaceLongPress,
                                modifier = Modifier.weight(5.5f),
                                style = KeyboardActionStyle.SPACE,
                                height = keyHeight,
                                longPressThreshold = 800L
                            )
                            // Period — long-press shows Gboard-style char popup.
                            PeriodKeyWithPopup(
                                onKeyPress = onKeyPress,
                                modifier = Modifier.weight(0.7f),
                                height = keyHeight
                            )
                            // Enter/Return (long-press for action selector)
                            LongPressActionButton(
                                icon = Icons.Default.KeyboardReturn,
                                onClick = onEnter,
                                onLongPress = onEnterLongPress,
                                modifier = Modifier.weight(1f),
                                style = KeyboardActionStyle.ENTER,
                                height = keyHeight,
                                longPressThreshold = 800L
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KeyboardKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 36.dp,
    /**
     * If non-null, holding this key inserts [longPressLabel] instead of [label].
     * Surfaced as a small superscript hint on the key. Used for QWERTY's
     * number row (1→!, 2→@, etc.) and any future Gboard-style chord keys.
     */
    longPressLabel: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    val baseModifier = modifier
        .height(height)
        .clip(RoundedCornerShape(8.dp))
        .background(KeyboardKeyColor)
    val tappableMod = if (onLongPress != null) {
        baseModifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress
        )
    } else {
        baseModifier.clickable(onClick = onClick)
    }
    Box(
        modifier = tappableMod,
        contentAlignment = Alignment.Center
    ) {
        if (longPressLabel != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = longPressLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    color = KeyboardKeyTextColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
                )
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = KeyboardKeyTextColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = KeyboardKeyTextColor
            )
        }
    }
}

@Composable
private fun RepeatingActionButton(
    onAction: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String? = null,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp? = null,
    initialDelayMs: Long = 500L,
    repeatDelayMs: Long = 50L
) {
    require(icon != null || label != null) { "RepeatingActionButton needs icon or label" }

    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    val sized = if (height != null) modifier.height(height) else modifier
    Surface(
        modifier = sized.repeatOnHold(
            initialDelayMs = initialDelayMs,
            repeatIntervalMs = repeatDelayMs,
            onTrigger = onAction
        ),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label ?: "repeating action",
                    tint = contentColor
                )
            } else {
                Text(
                    text = label!!,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun LongPressActionButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    height: androidx.compose.ui.unit.Dp = 42.dp,
    longPressThreshold: Long = 800L
) {
    var pressStartTime by remember { mutableStateOf(0L) }
    var isLongPressTriggered by remember { mutableStateOf(false) }

    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    Box(
        modifier = modifier
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressStartTime = System.currentTimeMillis()
                        isLongPressTriggered = false

                        // Wait for release or long press
                        val released = try {
                            withTimeout(longPressThreshold) {
                                tryAwaitRelease()
                                true
                            }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            // Long press threshold reached
                            isLongPressTriggered = true
                            onLongPress()
                            tryAwaitRelease()
                            false
                        }

                        // If released before threshold, handle as click
                        if (released && !isLongPressTriggered) {
                            onClick()
                        }
                    }
                )
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(10.dp),
            color = backgroundColor,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label ?: "action",
                        tint = contentColor
                    )
                } else if (label != null) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardActionButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    height: androidx.compose.ui.unit.Dp = 42.dp
) {
    val backgroundColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyColor
        KeyboardActionStyle.SPACE -> KeyboardSpaceColor
        KeyboardActionStyle.BACKSPACE -> KeyboardBackspaceColor
        KeyboardActionStyle.ENTER -> KeyboardEnterColor
    }
    val contentColor = when (style) {
        KeyboardActionStyle.NORMAL -> KeyboardKeyTextColor
        else -> KeyboardSpecialTextColor
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label ?: "action",
                    tint = contentColor
                )
            } else if (label != null) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}


@Composable
fun MicrophoneButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelTranscription: () -> Unit = {},
    recordingDuration: Long = 0L,
    transcriptionProgress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when (recordingState) {
            RecordingState.IDLE -> {
                IdleMicButton(onClick = onStartRecording)
            }
            RecordingState.RECORDING -> {
                RecordingMicButton(
                    onClick = onStopRecording,
                    recordingDuration = recordingDuration
                )
            }
            RecordingState.RECORDING_COMPLETE_AWAITING_CONFIRMATION -> {
                // Show awaiting confirmation button
                RecordingMicButton(
                    onClick = onStopRecording,
                    recordingDuration = recordingDuration
                )
            }
            RecordingState.PROCESSING -> {
                ProcessingIndicator(
                    progress = transcriptionProgress,
                    processingStage = processingStage,
                    audioFileSize = audioFileSize,
                    audioDurationSeconds = audioDurationSeconds,
                    onCancel = onCancelTranscription
                )
            }
            RecordingState.ERROR -> {
                IdleMicButton(onClick = onStartRecording)
            }
        }
    }
}

@Composable
fun IdleMicButton(onClick: () -> Unit) {
    val strings = LocalStrings.current
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = strings.startRecording,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun RecordingMicButton(onClick: () -> Unit, recordingDuration: Long = 0L) {
    val strings = LocalStrings.current
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Calculate minutes and seconds
    val seconds = (recordingDuration / 1000) % 60
    val minutes = (recordingDuration / 1000) / 60
    val timeText = "$minutes:${seconds.toString().padStart(2, '0')}"

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .scale(scale),
        containerColor = Color(0xFFE53935), // Red
        contentColor = Color.White
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = strings.stopRecording,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = timeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ProcessingIndicator(
    progress: Float? = null,
    processingStage: ProcessingStage? = null,
    audioFileSize: Long = 0L,
    audioDurationSeconds: Double = 0.0,
    onCancel: () -> Unit = {}
) {
    val strings = LocalStrings.current
    // Format file size for display
    val fileSizeText = when {
        audioFileSize < 1024 -> "${audioFileSize}B"
        audioFileSize < 1024 * 1024 -> "${audioFileSize / 1024}KB"
        else -> "${audioFileSize / (1024 * 1024)}MB"
    }

    // Format duration for display
    val durationText = if (audioDurationSeconds > 0) {
        val minutes = (audioDurationSeconds / 60).toInt()
        val seconds = (audioDurationSeconds % 60).toInt()
        if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    } else ""

    // Estimate time based on file size (rough estimate: ~1KB/sec processing)
    val estimatedSeconds = (audioFileSize / 1024.0).toInt().coerceAtLeast(1)
    val estimatedText = when {
        audioFileSize > 0 -> "Est: ~${estimatedSeconds}s"
        else -> ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Progress indicator with percentage
            if (progress != null && progress > 0f) {
                // Background circle (full)
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                // Actual progress
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Show percentage text (large and prominent)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Indeterminate progress
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Show "Processing..." text
                Text(
                    text = "...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Cancel button (clickable overlay)
            FloatingActionButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.cancel,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Show file info in small text
        if (fileSizeText.isNotEmpty() || durationText.isNotEmpty() || estimatedText.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (fileSizeText.isNotEmpty()) {
                    Text(
                        text = fileSizeText,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (durationText.isNotEmpty()) {
                    Text(
                        text = "• $durationText",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (estimatedText.isNotEmpty()) {
                    Text(
                        text = "• $estimatedText",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Show processing stage text below file info (prominent)
        processingStage?.let { stage ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stage.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
