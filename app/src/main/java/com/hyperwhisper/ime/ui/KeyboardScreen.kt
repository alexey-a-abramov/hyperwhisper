package com.hyperwhisper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import kotlinx.coroutines.delay
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.overlays.ConfigInfoDialog
import com.hyperwhisper.ui.overlays.ConfigPatchConfirmationOverlay
import com.hyperwhisper.ui.overlays.ErrorOverlay
import com.hyperwhisper.ui.panels.HistoryAudioPlayer
import com.hyperwhisper.ui.panels.TranscriptionHistoryPanel
import com.hyperwhisper.ui.sections.BottomActionsRow
import com.hyperwhisper.ui.sections.LanguageModelRow
import com.hyperwhisper.ui.sections.RecordingSection
import com.hyperwhisper.ui.dialogs.ModeSelectionDialog
import com.hyperwhisper.ui.dialogs.CancelRecordingConfirmationDialog
import com.hyperwhisper.ui.selectors.LanguageSelectorDialog
import com.hyperwhisper.ui.selectors.LlmModelSelectorDialog
import com.hyperwhisper.ui.selectors.ProviderModelSelectorDialog
import com.hyperwhisper.ui.dialogs.EnterActionSelectorDialog
import com.hyperwhisper.ui.dialogs.EnterAction
import com.hyperwhisper.ui.util.localizedDisplayName

internal val KeyboardSurfaceColor = Color(0xFF000000)
internal val KeyboardKeyColor = Color(0xFFFFFFFF)
internal val KeyboardKeyTextColor = Color(0xFF000000)
// Pressed-state fill + thin edge for the letter/number keys, so each key reads
// as a distinct, divided touch target and the one under the finger is obvious.
internal val KeyboardKeyPressedColor = Color(0xFF90CAF9)
internal val KeyboardKeyBorderColor = Color(0xFFB0B0B0)
// Canonical action-button palette. Same yellow/red/green across every layout
// (QWERTY, Code, Emoji, Dictation's bottom row) so muscle memory transfers.
internal val KeyboardSpaceColor = Color(0xFFFFEB3B)
internal val KeyboardBackspaceColor = Color(0xFFD32F2F)
internal val KeyboardEnterColor = Color(0xFF00C853)

/**
 * Pictogram for each preset-pickable layout. Used in the preset picker
 * dropdown so users can scan modes visually rather than reading every label.
 * Returns null for modes that don't appear in the picker (Voice, Text — they
 * have dedicated chips).
 */
internal fun KeyboardInputMode.pickerIcon(): ImageVector? = when (this) {
    KeyboardInputMode.CODE -> Icons.Default.Code
    KeyboardInputMode.EMOJI -> Icons.Default.EmojiEmotions
    KeyboardInputMode.AGENT_CLAUDE_CODE -> Icons.Default.SmartToy
    KeyboardInputMode.AGENT_OPENCODE -> Icons.Default.DataObject
    KeyboardInputMode.AGENT_GEMINI -> Icons.Default.AutoAwesome
    KeyboardInputMode.AGENT_CODEX -> Icons.Default.Bolt
    KeyboardInputMode.AGENT_MACROS -> Icons.Default.TextSnippet
    KeyboardInputMode.EXPERIMENTAL_TERMINAL -> Icons.Default.Terminal
    else -> null
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
    onSendKeyChord: (com.hyperwhisper.data.KeyChord) -> Unit = {},
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
    val reprocessedText by viewModel.reprocessedText.collectAsState()
    val voiceModes by viewModel.voiceModes.collectAsState()
    val selectedModeId by viewModel.selectedModeId.collectAsState()
    val apiSettings by viewModel.apiSettings.collectAsState()
    val appearanceSettings by viewModel.appearanceSettings.collectAsState()
    val recentlyUsedLanguages by viewModel.recentlyUsedLanguages.collectAsState()
    val recentlyUsedProviderModels by viewModel.recentlyUsedProviderModels.collectAsState()
    val configuredProviders by viewModel.configuredProviders.collectAsState()
    val localWhisperModels by viewModel.localWhisperModels.collectAsState()
    val usageStatistics by viewModel.usageStatistics.collectAsState()
    val pendingConfigPatch by viewModel.pendingConfigPatch.collectAsState()
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
    // Normalized at the source so a legacy persisted mode (NUMPAD, etc.)
    // never renders, not even for the first frame.
    var keyboardInputMode by remember {
        mutableStateOf(appearanceSettings.lastKeyboardInputMode.normalize())
    }
    // Snapshot of the most recent non-agent mode, so picking a command from
    // an agent palette can drop the user back into the typing layout they
    // came from instead of stranding them on the chips grid.
    var lastNonAgentMode by remember {
        mutableStateOf(
            if (appearanceSettings.lastKeyboardInputMode.isAgent) KeyboardInputMode.QWERTY
            else appearanceSettings.lastKeyboardInputMode.normalize()
        )
    }
    var currentKeyboardLayout by remember { mutableStateOf(appearanceSettings.currentKeyboardLayout) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    // Full locality list, opened by long-pressing the dictation locality key.
    var showLocalitySheet by remember { mutableStateOf(false) }
    var showEnterActionSelector by remember { mutableStateOf(false) }
    // Picker for the third "configurable preset" slot in the top strip.
    var showPresetPicker by remember { mutableStateOf(false) }
    // Inline LLM provider+model picker (only meaningful when the active
    // voice mode runs a post-processing step).
    var showLlmModelDialog by remember { mutableStateOf(false) }
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
        if (!keyboardInputMode.isAgent) {
            lastNonAgentMode = keyboardInputMode
        }
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

    val handleEnterLongPress = {
        showEnterActionSelector = true
    }

    // Locality = unified keyboard language: applying one flips the typing
    // layout AND points speech input at that language in a single step.
    val applyLocality: (com.hyperwhisper.data.KeyboardLayout) -> Unit = { layout ->
        currentKeyboardLayout = layout
        viewModel.updateKeyboardLayout(
            appearanceSettings.copy(
                currentKeyboardLayout = layout,
                // Picking a locality also adds it to the tap-cycle, so the
                // long-press list doubles as the enable manager.
                enabledKeyboardLayouts = appearanceSettings.enabledKeyboardLayouts + layout
            )
        )
        viewModel.setInputLanguage(layout.inputLanguageCode)
    }

    // Tap on the locality key → advance to the next enabled locality. Cycle
    // logic lives in the pure (unit-tested) LocalitySelection helper.
    val onCycleLocality = {
        applyLocality(
            com.hyperwhisper.data.LocalitySelection.next(
                currentKeyboardLayout, appearanceSettings.enabledKeyboardLayouts
            )
        )
    }

    // Checkbox in the locality list → add/remove from the tap-cycle without
    // changing the active locality. Never lets the set go empty.
    val onToggleLocalityEnabled: (com.hyperwhisper.data.KeyboardLayout) -> Unit = { layout ->
        viewModel.updateKeyboardLayout(
            appearanceSettings.copy(
                enabledKeyboardLayouts = com.hyperwhisper.data.LocalitySelection.toggleEnabled(
                    appearanceSettings.enabledKeyboardLayouts, layout
                )
            )
        )
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

    // Re-transcribe from history doesn't type into the active field — instead
    // the result lands in the paste-last buffer, so the paste button previews
    // its first characters and one tap inserts it. Mirrors a live
    // transcription's clipboard behaviour, minus the auto-commit.
    LaunchedEffect(reprocessedText) {
        reprocessedText?.let { text ->
            if (text.isNotEmpty()) {
                lastTranscribedText = text
                if (appearanceSettings.autoCopyToClipboard) {
                    val clip = ClipData.newPlainText("Transcribed Text", text)
                    clipboardManager.setPrimaryClip(clip)
                }
            }
            viewModel.clearReprocessedText()
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

    // Single composable factory shared by every layout branch — every layout
    // gets the same top strip with the same wiring, so we don't repeat the
    // 25-line call-site five times.
    val topStrip: @Composable () -> Unit = {
        UniversalKeyboardTopStrip(
            currentMode = keyboardInputMode,
            presetMode = appearanceSettings.presetKeyboardMode,
            onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
            onPresetLongPress = { showPresetPicker = true },
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
            pasteText = lastTranscribedText.ifEmpty { transcriptionHistory.firstOrNull()?.text.orEmpty() },
            enableHistoryPanel = appearanceSettings.enableHistoryPanel,
            onPaste = onTextCommit,
            onShowHistory = { showHistoryPanel = true }
        )
    }

    Box(modifier = modifier.fillMaxWidth().height(KeyboardMetrics.BoardHeight)) {
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
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
            if (keyboardInputMode == KeyboardInputMode.DICTATION) {
                // Dictation-specific header — mode chips on the left, settings
                // gear + backspace stacked on the right. Replaces the universal
                // top strip for this mode so the chrome can be reorganized
                // around the mic.
                com.hyperwhisper.ui.sections.DictationHeader(
                    currentMode = keyboardInputMode,
                    presetMode = appearanceSettings.presetKeyboardMode,
                    onSelectMode = { keyboardInputMode = it; modeChangeToast = it },
                    onPresetLongPress = { showPresetPicker = true },
                    onSettings = {
                        val intent = android.content.Intent(
                            context, com.hyperwhisper.ui.settings.SettingsActivity::class.java
                        ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    },
                    pasteText = lastTranscribedText.ifEmpty { transcriptionHistory.firstOrNull()?.text.orEmpty() },
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPaste = onTextCommit,
                    onShowHistory = { showHistoryPanel = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Recording row — mic centered with Esc/Tab stacked on the
                // left. Sits directly under the header so the mic is high on
                // the surface and easy to reach. Backspace is hidden because
                // the bottom action row owns it.
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
                    onToggleTimer = { showTimerText = !showTimerText },
                    onDelete = onDelete,
                    onDeleteAll = onDeleteAll,
                    showBackspace = false,
                    onEsc = onEscape,
                    onTab = onTab,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Language & Model Info Row — sits just above the bottom
                // action row so input/model chips are close to the SPACE bar.
                LanguageModelRow(
                    apiSettings = apiSettings,
                    recordingState = recordingState,
                    techieModeEnabled = appearanceSettings.techieModeEnabled,
                    voiceModes = voiceModes,
                    selectedModeId = selectedModeId,
                    onShowInputLanguageDialog = { showInputLanguageDialog = true },
                    onShowOutputLanguageDialog = { showOutputLanguageDialog = true },
                    onShowConfigInfo = { showConfigInfo = true },
                    onShowProviderModelDialog = {
                        viewModel.refreshLocalWhisperModels()
                        showProviderModelDialog = true
                    },
                    onShowModeDialog = { showModeDialog = true },
                    onShowLlmModelDialog = { showLlmModelDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                BottomActionsRow(
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    localityCode = currentKeyboardLayout.code,
                    onCommitText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    onCycleLocality = onCycleLocality,
                    onShowLocalityList = { showLocalitySheet = true },
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    onDelete = onDelete,
                    onDeleteAll = onDeleteAll
                )
            } else if (keyboardInputMode.isAgent) {
                topStrip()
                AgentKeyboard(
                    title = keyboardInputMode.displayName,
                    commands = com.hyperwhisper.data.AgentCommands.byMode(keyboardInputMode),
                    onInsert = { text, stayOnPalette ->
                        onTextCommit(text)
                        // Picking a slash-command or macro means the user is
                        // done with the palette layer — drop them back into
                        // their typing layout. State-toggle chips (the Claude
                        // Code mode cycler) set stayOnPalette so multi-step
                        // cycling doesn't bounce them through QWERTY between
                        // taps.
                        if (!stayOnPalette) {
                            keyboardInputMode = lastNonAgentMode
                        }
                    },
                    onSendChord = { chord ->
                        // Chord chips are *not* slash-commands; the user is
                        // toggling app state (e.g. Claude Code's plan mode)
                        // and almost certainly wants to keep tapping more
                        // chips, so do NOT auto-switch back to QWERTY here.
                        onSendKeyChord(chord)
                    },
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPasteText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    modifier = Modifier.weight(1f)
                )
            } else if (keyboardInputMode == KeyboardInputMode.EXPERIMENTAL_TERMINAL) {
                topStrip()
                ExperimentalTerminalKeyboard(
                    onTextCommit = onTextCommit,
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPasteText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    modifier = Modifier.weight(1f)
                )
            } else if (keyboardInputMode == KeyboardInputMode.EMOJI) {
                topStrip()
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
                    onSpace = handleSpacePress,
                    onEnter = onEnter,
                    onReturnToDictation = { keyboardInputMode = KeyboardInputMode.DICTATION },
                    onModeChange = { keyboardInputMode = it },
                    currentMode = keyboardInputMode,
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPasteText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                topStrip()
                val codeModifierState by viewModel.modifierKeyState.state.collectAsState()
                TextKeyboardSectionNew(
                    mode = keyboardInputMode,
                    layout = currentKeyboardLayout,
                    lastTranscribedText = lastTranscribedText,
                    transcriptionHistory = transcriptionHistory,
                    enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                    onPasteText = onTextCommit,
                    onShowHistory = { showHistoryPanel = true },
                    onKeyPress = onTextCommit,
                    onSpacePress = handleSpacePress,
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
                    onEscape = onEscape,
                    onTab = onTab,
                    localityCode = currentKeyboardLayout.code,
                    onCycleLocality = onCycleLocality,
                    onShowLocalityList = { showLocalitySheet = true },
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
                context = context,
                providerName = apiSettings.provider.displayName,
                onSwitchProvider = {
                    viewModel.clearError()
                    viewModel.refreshLocalWhisperModels()
                    showProviderModelDialog = true
                },
            )
        }

        // Show Configuration Patch Confirmation diff sheet — nothing is
        // persisted until the user taps Apply.
        pendingConfigPatch?.let { patch ->
            ConfigPatchConfirmationOverlay(
                patch = patch,
                onApply = { viewModel.confirmPendingPatch() },
                onCancel = { viewModel.rejectPendingPatch() }
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
                lastTestedAt = apiSettings.lastTestedAt,
                localWhisperModels = localWhisperModels,
                currentLocalWhisperPath = apiSettings.localModelSettings.whisperModelPath,
                onLocalWhisperModelSelected = { path ->
                    viewModel.setActiveLocalWhisperModel(path)
                    showProviderModelDialog = false
                },
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

        // Full locality list (long-press the dictation locality key).
        if (showLocalitySheet) {
            com.hyperwhisper.ui.sections.LocalitySelectorSheet(
                currentLayout = currentKeyboardLayout,
                enabledLayouts = appearanceSettings.enabledKeyboardLayouts,
                onLocalitySelected = { applyLocality(it) },
                onToggleEnabled = { onToggleLocalityEnabled(it) },
                onDismiss = { showLocalitySheet = false }
            )
        }

        // Show LLM provider+model picker (inline IME version).
        if (showLlmModelDialog) {
            LlmModelSelectorDialog(
                currentProvider = apiSettings.llmConfig.provider,
                currentModelId = apiSettings.llmConfig.modelId,
                apiKeys = apiSettings.llmConfig.apiKeys,
                lastTestedAt = apiSettings.llmConfig.lastTestedAt,
                onProviderModelSelected = { provider, modelId ->
                    viewModel.setLlmProviderAndModel(provider, modelId)
                    showLlmModelDialog = false
                },
                onDismiss = { showLlmModelDialog = false }
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
                    }
                    showEnterActionSelector = false
                },
                onDismiss = { showEnterActionSelector = false }
            )
        }

        // Show History Panel
        if (showHistoryPanel) {
            // Surface any saved-but-unlinked audio (and normalise legacy file
            // names) each time the panel opens, so orphaned recordings from
            // this session show up with play + reprocess.
            LaunchedEffect(Unit) { viewModel.reconcileAudioHistory() }

            // Single-stream player for history playback: one recording at a
            // time, play/pause toggle, 1-minute pause decay. Released when the
            // panel leaves composition so nothing keeps playing in the
            // background.
            val historyPlayer = remember { HistoryAudioPlayer(coroutineScope) }
            val playbackState by historyPlayer.state.collectAsState()
            DisposableEffect(Unit) { onDispose { historyPlayer.release() } }

            TranscriptionHistoryPanel(
                history = transcriptionHistory,
                onSelect = { text ->
                    onTextCommit(text)
                    showHistoryPanel = false
                },
                onClearAll = { viewModel.clearHistory() },
                onDismiss = {
                    historyPlayer.stop()
                    showHistoryPanel = false
                },
                onCopy = { item ->
                    val clip = ClipData.newPlainText("Transcribed Text", item.text)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                onReprocessWithCurrentSettings = { item ->
                    viewModel.reprocessWithCurrentSettings(item)
                    showHistoryPanel = false
                },
                playback = playbackState,
                onTogglePlay = { item ->
                    val audioPath = item.audioFilePath
                    when {
                        audioPath.isNullOrBlank() ->
                            Toast.makeText(context, "No saved audio for this entry", Toast.LENGTH_SHORT).show()
                        !java.io.File(audioPath).exists() ->
                            Toast.makeText(context, "Saved audio file not found", Toast.LENGTH_SHORT).show()
                        else -> historyPlayer.toggle(item.id, audioPath)
                    }
                },
                onSeek = { fraction -> historyPlayer.seekTo(fraction) }
            )
        }

        // Long-press preset picker. Voice and QWERTY have dedicated slots in
        // the strip; this picker covers everything else the user might want
        // bound to the third slot. Same scrim+card pattern as before since
        // IMEs can't host real Compose Popups.
        if (showPresetPicker) {
            val presetCandidates = remember(appearanceSettings.enabledAgentKeyboards) {
                val base = listOf(KeyboardInputMode.CODE, KeyboardInputMode.EMOJI)
                val agents = KeyboardInputMode.agentModes
                    .filter { it.name in appearanceSettings.enabledAgentKeyboards }
                // Experimental layouts are always surfaced in the preset picker
                // — that's their opt-in surface area. They stay out of the
                // default swipe-cycle until they're promoted to a base mode.
                val experimental = KeyboardInputMode.experimentalModes.toList()
                base + agents + experimental
            }
            val scrimSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = scrimSource
                    ) { showPresetPicker = false }
            )
            Card(
                modifier = Modifier
                    .padding(top = 38.dp, start = 92.dp)
                    .widthIn(min = 180.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    presetCandidates.forEach { mode ->
                        val isCurrent = mode == appearanceSettings.presetKeyboardMode.normalize()
                        Surface(
                            onClick = {
                                viewModel.setPresetKeyboardMode(mode)
                                keyboardInputMode = mode
                                modeChangeToast = mode
                                showPresetPicker = false
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
                                mode.pickerIcon()?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isCurrent)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = mode.localizedDisplayName(),
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
