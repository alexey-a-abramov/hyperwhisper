package com.hyperwhisper.service

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.network.ChatCompletionStrategy
import com.hyperwhisper.network.TranscriptionStrategy
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.ui.KeyboardScreen
import com.hyperwhisper.ui.KeyboardViewModel
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Custom Input Method Service for Voice-to-Text
 * Integrates Jetpack Compose with InputMethodService
 */
@AndroidEntryPoint
class VoiceInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    @Inject
    lateinit var audioRecorderManager: AudioRecorderManager

    @Inject
    lateinit var voiceRepository: VoiceRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var transcriptionStrategy: TranscriptionStrategy

    @Inject
    lateinit var chatCompletionStrategy: ChatCompletionStrategy

    private lateinit var viewModel: KeyboardViewModel
    private var composeView: ComposeView? = null
    private var recomposer: Recomposer? = null

    // Lifecycle for Compose integration
    private val lifecycleRegistry = LifecycleRegistry(this)

    // ViewModelStore for ViewModel lifecycle
    private val _viewModelStore = ViewModelStore()

    // SavedStateRegistry for state preservation
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val TAG = "VoiceIME"
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceInputMethodService onCreate")
        TraceLogger.lifecycle("IME", "onCreate")

        try {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            TraceLogger.trace("IME", "Lifecycle state set to CREATED")

            // Initialize ViewModel using ViewModelProvider
            viewModel = KeyboardViewModel(voiceRepository, settingsRepository)
            TraceLogger.trace("IME", "ViewModel initialized")
        } catch (e: Exception) {
            TraceLogger.error("IME", "Error in onCreate", e)
            throw e
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")
        TraceLogger.lifecycle("IME", "onCreateInputView")

        try {
            // Check microphone permission
            val hasMicPermission = hasMicrophonePermission()
            if (!hasMicPermission) {
                Log.w(TAG, "Microphone permission not granted")
                TraceLogger.trace("IME", "WARNING: Microphone permission not granted")
            } else {
                TraceLogger.trace("IME", "Microphone permission granted")
            }

            // Move lifecycle to STARTED state before creating Compose content
            // This ensures the lifecycle is active when the ComposeView is attached to window
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            TraceLogger.trace("IME", "Lifecycle state set to STARTED")

            // Create manual Recomposer to avoid parent composition context resolution issues
            val coroutineContext = AndroidUiDispatcher.CurrentThread
            recomposer = Recomposer(coroutineContext)
            TraceLogger.trace("IME", "Recomposer created")

            // Launch recomposer in coroutine
            CoroutineScope(coroutineContext).launch {
                recomposer?.runRecomposeAndApplyChanges()
            }
            TraceLogger.trace("IME", "Recomposer launched")

            // Create ComposeView
            composeView = ComposeView(this).apply {
                // Set up lifecycle owners for proper Compose integration
                setViewTreeLifecycleOwner(this@VoiceInputMethodService as androidx.lifecycle.LifecycleOwner)
                setViewTreeViewModelStoreOwner(this@VoiceInputMethodService)
                setViewTreeSavedStateRegistryOwner(this@VoiceInputMethodService)
                TraceLogger.trace("IME", "ViewTree owners set on ComposeView")

                // Set the manual composition context to avoid parent resolution
                setParentCompositionContext(recomposer)
                TraceLogger.trace("IME", "Parent composition context set to manual Recomposer")

                // Set content immediately - no need to wait for attach
                // since we're using manual recomposer
                setContent {
                    KeyboardContent()
                }
                TraceLogger.trace("IME", "Content set on ComposeView")
            }

            TraceLogger.trace("IME", "ComposeView created successfully")
            return composeView!!
        } catch (e: Exception) {
            TraceLogger.error("IME", "Error in onCreateInputView", e)
            throw e
        }
    }

    @Composable
    private fun KeyboardContent() {
        HyperWhisperTheme {
            KeyboardScreen(
                viewModel = viewModel,
                onTextCommit = { text ->
                    commitText(text)
                },
                onDelete = {
                    deleteText()
                },
                onSpace = {
                    commitText(" ")
                },
                onEnter = {
                    commitText("\n")
                },
                onInsertClipboard = {
                    insertClipboard()
                },
                onSwitchKeyboard = {
                    switchToPreviousKeyboard()
                }
            )
        }
    }

    /**
     * Commit text to the current input field
     */
    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        try {
            ic.beginBatchEdit()
            ic.commitText(text, 1)
            ic.endBatchEdit()
            Log.d(TAG, "Committed text: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error committing text", e)
        }
    }

    /**
     * Delete one character before the cursor
     */
    private fun deleteText() {
        val ic = currentInputConnection ?: return
        try {
            ic.deleteSurroundingText(1, 0)
            Log.d(TAG, "Deleted character")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting text", e)
        }
    }

    /**
     * Insert clipboard contents at cursor position
     */
    private fun insertClipboard() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotEmpty()) {
                    commitText(text)
                    Log.d(TAG, "Inserted clipboard text: ${text.take(50)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting clipboard", e)
        }
    }

    /**
     * Show input method picker to switch keyboard
     */
    private fun switchToPreviousKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
            Log.d(TAG, "Showing input method picker")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing input method picker", e)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput - inputType: ${attribute?.inputType}, restarting: $restarting")
        TraceLogger.lifecycle("IME", "onStartInput", "inputType=${attribute?.inputType}, restarting=$restarting")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView - restarting: $restarting")
        TraceLogger.lifecycle("IME", "onStartInputView", "restarting=$restarting")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView - finishing: $finishingInput")
        TraceLogger.lifecycle("IME", "onFinishInputView", "finishing=$finishingInput")
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Cancel any ongoing recording when keyboard is dismissed
        if (voiceRepository.isRecording()) {
            TraceLogger.trace("IME", "Canceling ongoing recording on keyboard dismiss")
            viewModel.cancelRecording()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput")
        TraceLogger.lifecycle("IME", "onFinishInput")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        TraceLogger.lifecycle("IME", "onDestroy")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        // Clean up resources
        audioRecorderManager.release()
        TraceLogger.trace("IME", "AudioRecorderManager released")

        // Cancel recomposer
        recomposer?.cancel()
        recomposer = null
        TraceLogger.trace("IME", "Recomposer cancelled")

        composeView = null
        _viewModelStore.clear()
        TraceLogger.trace("IME", "ViewModelStore cleared")
    }

    /**
     * Check if microphone permission is granted
     */
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
