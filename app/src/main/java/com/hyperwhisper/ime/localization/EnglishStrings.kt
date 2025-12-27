package com.hyperwhisper.localization

object EnglishStrings : Strings {
    // App Info
    override val appName = "HyperWhisper"
    override val imeName = "HyperWhisper Voice Keyboard"

    // Common Actions
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val edit = "Edit"
    override val add = "Add"
    override val close = "Close"
    override val back = "Back"
    override val copy = "Copy"
    override val clear = "Clear"

    // Keyboard UI
    override val tapToSpeak = "Tap to speak"
    override val recording = "Recording..."
    override val processing = "Processing..."
    override val space = "space"
    override val pasteLastTranscription = "PASTE LAST (hold: history)"
    override val pasteLastHold = "PASTE LAST (hold: history)"
    override val holdForHistory = "Hold for history"

    // Buttons
    override val switchKeyboard = "Switch Keyboard"
    override val helpAndAbout = "Help & About"
    override val settings = "Settings"
    override val viewLogs = "View Logs"
    override val stopRecording = "Stop Recording"
    override val startRecording = "Start Recording"

    // Content Descriptions (for accessibility)
    override val switchKeyboardDesc = "Switch Keyboard"
    override val helpAndAboutDesc = "Help & About"
    override val settingsDesc = "Settings"
    override val viewLogsDesc = "View Logs"
    override val cancelDesc = "Cancel"
    override val enterDesc = "Enter"

    // Language Selectors
    override val inputLanguageSpeech = "Input Language (Speech)"
    override val outputLanguageTranslation = "Output Language (Translation)"
    override val autoDetect = "Auto-detect"
    override val searchLanguages = "Search..."
    override val searchPlaceholder = "Search..."
    override val noLanguagesFound = "No languages found"

    // Settings Screen
    override val settingsTitle = "HyperWhisper Settings"
    override val apiConfiguration = "API Configuration"
    override val apiProvider = "API Provider"
    override val baseUrl = "Base URL"
    override val baseUrlHint = "Must end with /"
    override val apiKey = "API Key"
    override val apiKeyPlaceholder = "sk-..."
    override val modelId = "Model ID"
    override val testConnection = "Test Connection"
    override val testingConnection = "Testing connection..."
    override val resetToDefaults = "Reset to Defaults"
    override val reset = "RESET"
    override val saveAndCloseSettings = "Save & Close Settings"
    override val inputLanguageHintLabel = "Input Language Hint"
    override val inputLanguageHintText = "Only use for auto-detect (speech language)\nNote: Most models ignore this\n• whisper-1 & variants: Ignored\n• distil-whisper-large-v3: Uses it"
    override val viewApiLogs = "View API Logs"
    override val logsInfoTitle = "API & Diagnostic Logs"
    override val logsInfoDescription = "View detailed logs of API calls, processing steps, and errors. Logs are cleared on app restart."

    // API Providers
    override val providerDescription = "Choose your speech-to-text provider"

    // Voice Modes
    override val voiceModes = "Voice Processing Modes"
    override val selectMode = "Select Mode"
    override val addVoiceMode = "Add Voice Mode"
    override val editVoiceMode = "Edit Voice Mode"
    override val deleteVoiceMode = "Delete Mode"
    override val modeName = "Mode Name"
    override val systemPrompt = "System Prompt"
    override val enterPrompt = "Enter the system prompt for this mode"

    // Default Voice Modes
    override val modeVerbatim = "Verbatim"
    override val modeVerbatimPrompt = "Transcribe the audio exactly as spoken."
    override val modeFixGrammar = "Fix Grammar"
    override val modeFixGrammarPrompt = "Transcribe this audio and fix any grammar, spelling, and punctuation errors while preserving the original meaning and tone."
    override val modePromptFormatter = "Prompt Formatter"
    override val modePromptFormatterPrompt = "Reformulate the user's input into a clear, effective prompt suitable for LLM processing. Enhance clarity, add necessary context, and structure it for optimal AI understanding. Maintain the user's intent while making it more precise and actionable."
    override val modeLlmResponse = "LLM Response"
    override val modeLlmResponsePrompt = "The user is asking a question. Provide a direct, concise answer to the question without any additional explanation or context. Return ONLY the answer itself."

    // Appearance Settings
    override val appearanceSettings = "Appearance"
    override val colorScheme = "Color Scheme"
    override val useDynamicColor = "Use Dynamic Color"
    override val useDynamicColorDesc = "Match system wallpaper colors"
    override val themeMode = "Theme Mode"
    override val darkMode = "Dark Mode"
    override val textSize = "Text Size"
    override val fontFamily = "Font Family"
    override val uiLanguage = "Interface Language"

    // Dark Mode Options
    override val darkModeFollowSystem = "Follow System"
    override val darkModeAlwaysLight = "Always Light"
    override val darkModeAlwaysDark = "Always Dark"

    // Advanced Settings
    override val advancedSettings = "Advanced"
    override val autoCopyToClipboard = "Auto-copy to Clipboard"
    override val autoCopyToClipboardDesc = "Automatically copy transcriptions"
    override val enableHistoryPanel = "Enable History Panel"
    override val enableHistoryPanelDesc = "Long-press paste button to view history"

    // About Screen
    override val aboutHyperWhisper = "About HyperWhisper"
    override val version = "Version"
    override val versionCode = "Code"
    override val description = "HyperWhisper is a voice-to-text input method (keyboard) that uses advanced speech recognition APIs to provide fast and accurate transcriptions. It is designed for developers and power users who want to customize their voice input experience."
    override val features = "Features:"
    override val featuresList = "• Customizable API provider (OpenAI, Groq, OpenRouter, etc.)\n• Multiple voice modes (Verbatim, Grammar Fix, Polite, etc.)\n• Support for different input and output languages\n• Modern, responsive UI built with Jetpack Compose"
    override val usageStatisticsAndCosts = "Usage Statistics & Costs:"
    override val noUsageDataYet = "No usage data yet. Start using the keyboard to see statistics!"
    override val totalAudio = "Total Audio"
    override val estimatedTotalCost = "Estimated Total Cost"
    override val modelBreakdown = "Model Breakdown:"
    override val inputTokens = "Input"
    override val outputTokens = "Output"
    override val totalTokens = "Total"
    override val audioBasedPricing = "Audio-based pricing (tokens not tracked)"
    override val costsEstimateNote = "* Costs are estimated based on current API pricing. Actual costs may vary."
    override val clearStatistics = "CLEAR STATISTICS"

    // Logs Screen
    override val traceLogs = "Trace Logs"
    override val diagnosticLogs = "Diagnostic Logs"
    override val diagnosticLogsDesc = "These logs show API calls, processing steps, and errors. Logs are cleared on app restart. You can copy or clear them using the buttons above."
    override val logsStoredAt = "Logs are stored in"
    override val noLogsYet = "No logs yet.\nStart using the keyboard to see activity logs."
    override val copyLogs = "Copy Logs"
    override val clearLogs = "Clear Logs"
    override val logsCopiedToClipboard = "Logs copied to clipboard"

    // Configuration Info
    override val currentConfiguration = "Current Configuration"
    override val provider = "Provider"
    override val transcriptionModel = "Transcription Model"
    override val postProcessingModel = "Post-Processing Model"
    override val postProcessingModelDesc = "gpt-4o-mini (for non-verbatim modes & translation)"
    override val none = "None"
    override val keepOriginal = "keep original"
    override val notConfigured = "Not configured"

    // Transcription History
    override val transcriptionHistory = "Transcription History"
    override val historyCount = "{count}/20"
    override val noHistoryYet = "No history yet"
    override val clearAll = "CLEAR ALL"

    // Connection Test
    override val connectionTesting = "Testing connection..."
    override val connectionSuccess = "Connection successful! API is responding."
    override val connectionFailed = "Connection failed"
    override val authenticationFailed = "Authentication failed. Check your API key."
    override val endpointNotFound = "Endpoint not found. Check base URL and model ID."
    override val connectionTimeout = "Connection timeout. Check your internet connection."
    override val sslError = "SSL/TLS error. Check endpoint URL (https)."

    // Errors
    override val error = "Error"
    override val copyError = "COPY ERROR"
    override val openSettings = "OPEN SETTINGS"
    override val dismiss = "DISMISS"
    override val errorConfigureApiKey = "Please configure API key in settings"
    override val errorNoModeSelected = "No voice mode selected"
    override val errorRecordingFailed = "Recording failed"
    override val errorNetworkFailed = "Network error. Please check your connection."
    override val errorApiCall = "API Error"
    override val errorPermissionMicrophone = "Microphone permission not granted. Please enable microphone access in Android Settings."
    override val errorMicrophoneInUse = "Cannot access microphone. It may be in use by another app."

    // Processing Info Toast
    override val translated = "Translated to"
    override val twoStepProcessing = "Two-step processing"

    // Time units
    override val minutes = "m"
    override val seconds = "s"

    // Input Field Information
    override val inputFieldType = "Type"
    override val inputFieldApp = "App"
    override val inputFieldAction = "Action"
    override val fieldTypeText = "Text"
    override val fieldTypeEmail = "Email"
    override val fieldTypePassword = "Password"
    override val fieldTypeNumber = "Number"
    override val fieldTypePhone = "Phone"
    override val fieldTypeUrl = "URL"
    override val fieldTypeMultiline = "Multiline"
    override val fieldTypeUnknown = "Unknown"
    override val actionNone = "None"
    override val actionDone = "Done"
    override val actionGo = "Go"
    override val actionSearch = "Search"
    override val actionSend = "Send"
    override val actionNext = "Next"
    override val actionPrevious = "Previous"
}
