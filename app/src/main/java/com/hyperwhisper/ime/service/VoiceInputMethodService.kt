package com.hyperwhisper.service

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hyperwhisper.data.AppearanceSettings
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.network.ChatCompletionStrategy
import com.hyperwhisper.network.TranscriptionStrategy
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.ui.KeyboardScreen
import com.hyperwhisper.ui.KeyboardViewModel
import com.hyperwhisper.ui.theme.HyperWhisperTheme
import com.hyperwhisper.utils.TraceLogger
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * EntryPoint for accessing ViewModelFactory in Service context
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ViewModelFactoryProvider {
    fun viewModelFactory(): ViewModelProvider.Factory
}

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
    lateinit var voiceCommandProcessor: com.hyperwhisper.data.VoiceCommandProcessor

    @Inject
    lateinit var transcriptionStrategy: TranscriptionStrategy

    @Inject
    lateinit var chatCompletionStrategy: ChatCompletionStrategy

    @Inject
    lateinit var modifierKeyState: com.hyperwhisper.ime.keyboard.ModifierKeyState

    private lateinit var viewModel: KeyboardViewModel
    private var composeView: ComposeView? = null
    private var recomposer: Recomposer? = null
    internal var currentEditorInfo: EditorInfo? = null
    private val currentEditorInfoFlow = MutableStateFlow<EditorInfo?>(null)

    /**
     * Lazy-initialized so it's available the first time the input view is
     * created (which is also the first time any of these helpers can be
     * called from Compose).
     */
    private val controller: InputConnectionController by lazy {
        InputConnectionController(this)
    }

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

            // Initialize ViewModel using ViewModelProvider with Hilt factory
            // Get the factory from Hilt EntryPoint since Services don't get it automatically
            val factory = EntryPointAccessors.fromApplication(
                applicationContext,
                ViewModelFactoryProvider::class.java
            ).viewModelFactory()
            viewModel = ViewModelProvider(this, factory)[KeyboardViewModel::class.java]
            TraceLogger.trace("IME", "ViewModel initialized")
        } catch (e: Exception) {
            TraceLogger.error("IME", "Error in onCreate", e)
            throw e
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged - orientation: ${newConfig.orientation}")
        TraceLogger.lifecycle("IME", "onConfigurationChanged", "orientation=${newConfig.orientation}")
        // Configuration changes like rotation are handled gracefully
        // The service doesn't restart, and recording/transcription continues
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
        val appearanceSettings by settingsRepository.appearanceSettings.collectAsState(
            initial = AppearanceSettings()
        )
        val editorInfo by currentEditorInfoFlow.collectAsState(initial = null)

        HyperWhisperTheme(appearanceSettings = appearanceSettings) {
            KeyboardScreen(
                viewModel = viewModel,
                editorInfo = editorInfo,
                onTextCommit = { text ->
                    controller.commitText(text)
                },
                onDelete = {
                    controller.deleteSelected() // Prioritize deleting selected text
                },
                onDeleteAll = {
                    controller.deleteAll()
                },
                onSpace = {
                    controller.commitText(" ")
                },
                onEnter = {
                    controller.handleEnter()
                },
                onMoveCursorLeft = {
                    controller.moveCursorLeft()
                },
                onMoveCursorRight = {
                    controller.moveCursorRight()
                },
                onMoveCursorUp = {
                    controller.moveCursorUp()
                },
                onMoveCursorDown = {
                    controller.moveCursorDown()
                },
                onPageUp = {
                    controller.pageUp()
                },
                onPageDown = {
                    controller.pageDown()
                },
                onHome = {
                    controller.moveToHome()
                },
                onEnd = {
                    controller.moveToEnd()
                },
                onInsert = {
                    controller.sendInsert()
                },
                onForwardDelete = {
                    controller.sendForwardDelete()
                },
                onEscape = {
                    controller.sendEscape()
                },
                onTab = {
                    controller.sendTab()
                },
                onInsertClipboard = {
                    controller.insertClipboard()
                },
                onSwitchKeyboard = {
                    switchToTextKeyboard()
                }
            )
        }
    }

    /**
     * Switch to the previously used keyboard (usually the user's QWERTY keyboard).
     * Falls back to input method picker if direct switch is unavailable.
     *
     * Stays on the service (vs. moving to InputConnectionController) because
     * it touches the InputMethodManager / IMM picker, not the InputConnection.
     */
    private fun switchToTextKeyboard() {
        try {
            val switched = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                false
            }
            if (switched) {
                Log.d(TAG, "Switched to previous keyboard")
                return
            }

            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
            Log.d(TAG, "Previous keyboard unavailable, showing input method picker")
        } catch (e: Exception) {
            Log.e(TAG, "Error switching keyboard", e)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        this.currentEditorInfo = attribute
        this.currentEditorInfoFlow.value = attribute
        Log.d(TAG, "onStartInput - inputType: ${attribute?.inputType}, restarting: $restarting")
        TraceLogger.lifecycle("IME", "onStartInput", "inputType=${attribute?.inputType}, restarting=$restarting")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        this.currentEditorInfo = info
        this.currentEditorInfoFlow.value = info
        Log.d(TAG, "onStartInputView - restarting: $restarting")
        TraceLogger.lifecycle("IME", "onStartInputView", "restarting=$restarting")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        viewModel.syncRecordingState()

        // Notify the view model which app we're attached to so it can
        // (a) recall the user's last layout for that package and dispatch a
        //     layout-switch request, and
        // (b) tag subsequent layout writes from KeyboardScreen with the
        //     right packageName.
        // currentInputEditorInfo is preferred over the [info] parameter
        // because some IME contexts deliver null here even when the
        // service has a valid editor attached.
        val pkg = (info?.packageName ?: currentInputEditorInfo?.packageName)
        viewModel.onEditorAttached(pkg)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView - finishing: $finishingInput")
        TraceLogger.lifecycle("IME", "onFinishInputView", "finishing=$finishingInput")
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Allow recording to continue in background (screen lock, keyboard dismiss, etc.)
        // The 3-minute timeout will auto-stop-and-process if needed
        if (voiceRepository.isRecording()) {
            TraceLogger.trace("IME", "Recording continues in background (screen lock/keyboard dismiss)")
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
