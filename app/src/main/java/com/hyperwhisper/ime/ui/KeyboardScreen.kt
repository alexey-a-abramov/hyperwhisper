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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.CoroutineScope

private enum class KeyboardActionStyle {
    NORMAL,
    SPACE,
    BACKSPACE,
    ENTER
}

private val KeyboardSurfaceColor = Color(0xFF000000)
private val KeyboardKeyColor = Color(0xFFFFFFFF)
private val KeyboardKeyTextColor = Color(0xFF000000)
private val KeyboardSpaceColor = Color(0xFFFFEB3B)
private val KeyboardBackspaceColor = Color(0xFFD32F2F)
private val KeyboardEnterColor = Color(0xFF00C853)
private val KeyboardSpecialTextColor = Color(0xFF000000)
private val KeyboardModeSwitcherColor = Color(0xFF424242)

@Composable
private fun UnifiedModeSwitcher(
    currentMode: KeyboardInputMode,
    onModeChange: (KeyboardInputMode) -> Unit,
    onReturnToDictation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ABC button
        Surface(
            onClick = {
                when (currentMode) {
                    KeyboardInputMode.QWERTY -> onModeChange(KeyboardInputMode.SPECIAL_CHARS)
                    KeyboardInputMode.SPECIAL_CHARS -> onModeChange(KeyboardInputMode.QWERTY)
                    else -> onModeChange(KeyboardInputMode.QWERTY)
                }
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = if (currentMode == KeyboardInputMode.QWERTY || currentMode == KeyboardInputMode.SPECIAL_CHARS)
                MaterialTheme.colorScheme.primary else KeyboardModeSwitcherColor
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
                    contentDescription = "Dictation",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
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
    var lastSpacePressTime by remember { mutableStateOf(0L) }
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

    // Save keyboard input mode to settings whenever it changes
    LaunchedEffect(keyboardInputMode) {
        if (keyboardInputMode != appearanceSettings.lastKeyboardInputMode) {
            val updatedSettings = appearanceSettings.copy(lastKeyboardInputMode = keyboardInputMode)
            viewModel.saveKeyboardInputMode(updatedSettings)
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

    Box(modifier = modifier.fillMaxWidth().height(320.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
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
                // Top Row: Backspace (left) | Settings + View Logs (techie) + Help (right)
                TopControlsRow(
                    context = context,
                    showKeyboardSwitcher = false,
                    techieModeEnabled = appearanceSettings.techieModeEnabled,
                    onSwitchKeyboard = onSwitchKeyboard,
                    onDelete = onDelete,
                    onDeleteAll = onDeleteAll
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
                    showKeyboardButton = true,
                    onKeyboardButtonClick = { keyboardInputMode = KeyboardInputMode.QWERTY },
                    currentKeyboardMode = keyboardInputMode,
                    onModeChange = { keyboardInputMode = it }
                )
            } else {
                TextKeyboardSectionNew(
                    mode = keyboardInputMode,
                    recordingState = recordingState,
                    recordingDuration = recordingDuration,
                    onModeChange = { keyboardInputMode = it },
                    onKeyPress = onTextCommit,
                    onSpacePress = handleSpacePress,
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
                    onReturnToDictation = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() },
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
    }
}

@Composable
private fun TextKeyboardSectionNew(
    mode: KeyboardInputMode,
    recordingState: RecordingState = RecordingState.IDLE,
    recordingDuration: Long = 0L,
    onModeChange: (KeyboardInputMode) -> Unit,
    onKeyPress: (String) -> Unit,
    onSpacePress: () -> Unit,
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
    onReturnToDictation: () -> Unit,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shiftEnabled by remember { mutableStateOf(false) }
    var capsLockEnabled by remember { mutableStateOf(false) }
    
    val letterCase: (String) -> String = { key ->
        when {
            capsLockEnabled -> key.uppercase()
            shiftEnabled && key.all { it.isLetter() } -> key.uppercase()
            else -> key
        }
    }

    val isSpecialChars = mode == KeyboardInputMode.SPECIAL_CHARS

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
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "")
        )
    }

    when (mode) {
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
                        KeyboardActionButton(
                            icon = Icons.Default.Backspace,
                            onClick = onDelete,
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
                                else -> MaterialTheme.colorScheme.primary
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
                                                contentDescription = "Stop Recording",
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
                                            contentDescription = "Start Recording",
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
                                        KeyboardKeyButton(
                                            label = if (isSpecialChars) key else letterCase(key),
                                            onClick = {
                                                val out = if (isSpecialChars) key else letterCase(key)
                                                onKeyPress(out)
                                                if (shiftEnabled && !isSpecialChars && key.all { it.isLetter() }) {
                                                    shiftEnabled = false
                                                }
                                            },
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
                                    KeyboardKeyButton(
                                        label = key,
                                        onClick = { if (key == "⌫") onDelete() else onKeyPress(key) },
                                        modifier = Modifier.weight(1f),
                                        height = keyHeight
                                    )
                                }
                            }
                        } else {
                            // Shift row (z-m with shift and backspace)
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
                                listOf("z", "x", "c", "v", "b", "n", "m").forEach { key ->
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
                                KeyboardActionButton(
                                    icon = Icons.Default.Backspace,
                                    onClick = onDelete,
                                    modifier = Modifier.weight(1.5f),
                                    style = KeyboardActionStyle.BACKSPACE,
                                    height = keyHeight
                                )
                            }
                        }

                        // Bottom row with unified mode switcher
                        Row(
                            modifier = Modifier.fillMaxWidth().height(keyHeight),
                            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Unified mode switcher (left)
                            UnifiedModeSwitcher(
                                currentMode = mode,
                                onModeChange = onModeChange,
                                onReturnToDictation = onReturnToDictation,
                                modifier = Modifier.weight(3f).height(keyHeight)
                            )

                            // Comma
                            KeyboardKeyButton(
                                label = ",",
                                onClick = { onKeyPress(",") },
                                modifier = Modifier.weight(0.7f),
                                height = keyHeight
                            )
                            // Space bar
                            KeyboardActionButton(
                                label = "space",
                                onClick = onSpacePress,
                                modifier = Modifier.weight(2.5f),
                                style = KeyboardActionStyle.SPACE,
                                height = keyHeight
                            )
                            // Period
                            KeyboardKeyButton(
                                label = ".",
                                onClick = { onKeyPress(".") },
                                modifier = Modifier.weight(0.7f),
                                height = keyHeight
                            )
                            // Enter/Return
                            KeyboardActionButton(
                                icon = Icons.Default.KeyboardReturn,
                                onClick = onEnter,
                                modifier = Modifier.weight(1f),
                                style = KeyboardActionStyle.ENTER,
                                height = keyHeight
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun KeyboardKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 36.dp
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(8.dp),
        color = KeyboardKeyColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
    style: KeyboardActionStyle = KeyboardActionStyle.NORMAL,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 500L,
    repeatDelayMs: Long = 50L
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(initialDelayMs)
            while (isPressed) {
                onAction()
                delay(repeatDelayMs)
            }
        }
    }

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
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    onAction() // Fire immediately on press
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { } // Already handled in onPress
            )
        },
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "repeating action",
                tint = contentColor
            )
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
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Start Recording",
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun RecordingMicButton(onClick: () -> Unit, recordingDuration: Long = 0L) {
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
                contentDescription = "Stop Recording",
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
                    contentDescription = "Cancel",
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
