package com.hyperwhisper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import com.hyperwhisper.ui.dialogs.RecordingConfirmationDialog
import com.hyperwhisper.ui.selectors.LanguageSelectorDialog
import com.hyperwhisper.ui.selectors.ModeSelector

@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    editorInfo: EditorInfo? = null,
    onTextCommit: (String) -> Unit,
    onDelete: () -> Unit = {},
    onDeleteAll: () -> Unit = {},
    onSpace: () -> Unit = {},
    onEnter: () -> Unit = {},
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
    val usageStatistics by viewModel.usageStatistics.collectAsState()
    val pendingCommandResult by viewModel.pendingCommandResult.collectAsState()
    val lastAudioFileSize by viewModel.lastAudioFileSize.collectAsState()
    val lastAudioDuration by viewModel.lastAudioDuration.collectAsState()
    val walkieTalkieMode by viewModel.walkieTalkieMode.collectAsState()
    val modeChangeMessage by viewModel.modeChangeMessage.collectAsState()
    val needsConfirmation by viewModel.needsConfirmation.collectAsState()
    val finalRecordingDuration by viewModel.finalRecordingDuration.collectAsState()
    val recordingWasCut by viewModel.recordingWasCut.collectAsState()
    val processingPhase by viewModel.processingPhase.collectAsState()

    var showConfigInfo by remember { mutableStateOf(false) }
    var showInputLanguageDialog by remember { mutableStateOf(false) }
    var showOutputLanguageDialog by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showTimerText by remember { mutableStateOf(true) }
    var showModeDialog by remember { mutableStateOf(false) }

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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
            // Top Row: Backspace (left) | Keyboard Switcher + Settings + View Logs (techie) + Help (right)
            TopControlsRow(
                context = context,
                showKeyboardSwitcher = appearanceSettings.showKeyboardSwitcher,
                techieModeEnabled = appearanceSettings.techieModeEnabled,
                onSwitchKeyboard = onSwitchKeyboard,
                onDelete = onDelete,
                onDeleteAll = onDeleteAll
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Language & Model Info Row (full width)
            LanguageModelRow(
                apiSettings = apiSettings,
                recordingState = recordingState,
                techieModeEnabled = appearanceSettings.techieModeEnabled,
                voiceModes = voiceModes,
                selectedModeId = selectedModeId,
                onShowInputLanguageDialog = { showInputLanguageDialog = true },
                onShowOutputLanguageDialog = { showOutputLanguageDialog = true },
                onShowConfigInfo = { showConfigInfo = true },
                onShowModeDialog = { showModeDialog = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

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

            // Bottom row: Paste (left) + Space button (center/right)
            // Double-tap space for period + space
            BottomActionsRow(
                lastTranscribedText = lastTranscribedText,
                transcriptionHistory = transcriptionHistory,
                enableHistoryPanel = appearanceSettings.enableHistoryPanel,
                onPasteText = onTextCommit,
                onShowHistory = { showHistoryPanel = true },
                onSpace = onSpace,
                onDelete = onDelete
            )
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

        // Show Recording Confirmation Dialog (for recordings > 30 seconds)
        if (needsConfirmation) {
            RecordingConfirmationDialog(
                durationSeconds = finalRecordingDuration / 1000,
                onConfirm = { viewModel.confirmRecording() },
                onDiscard = { viewModel.rejectRecording() }
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

