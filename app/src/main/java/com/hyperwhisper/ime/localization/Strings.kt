package com.hyperwhisper.localization

/**
 * Multi-language string resources for HyperWhisper
 *
 * To add a new language:
 * 1. Create a new object implementing Strings (e.g., GermanStrings)
 * 2. Translate all properties
 * 3. Add the language to AppLanguage enum
 * 4. Add to getStrings() function
 */
interface Strings {
    // App Info
    val appName: String
    val imeName: String

    // Common Actions
    val cancel: String
    val save: String
    val delete: String
    val edit: String
    val add: String
    val close: String
    val back: String
    val copy: String
    val clear: String

    // Keyboard UI
    val tapToSpeak: String
    val recording: String
    val processing: String
    val space: String
    val pasteLastTranscription: String
    val pasteLastHold: String  // "PASTE LAST (hold: history)"
    val holdForHistory: String

    // Buttons
    val switchKeyboard: String
    val helpAndAbout: String
    val settings: String
    val viewLogs: String
    val stopRecording: String
    val startRecording: String

    // Content Descriptions (for accessibility)
    val switchKeyboardDesc: String
    val helpAndAboutDesc: String
    val settingsDesc: String
    val viewLogsDesc: String
    val cancelDesc: String
    val enterDesc: String

    // Language Selectors
    val inputLanguageSpeech: String
    val outputLanguageTranslation: String
    val autoDetect: String
    val searchLanguages: String
    val searchPlaceholder: String
    val noLanguagesFound: String

    // Settings Screen
    val settingsTitle: String
    val apiConfiguration: String
    val apiProvider: String
    val baseUrl: String
    val baseUrlHint: String
    val apiKey: String
    val apiKeyPlaceholder: String
    val modelId: String
    val testConnection: String
    val testingConnection: String
    val resetToDefaults: String
    val reset: String
    val saveAndCloseSettings: String
    val inputLanguageHintLabel: String
    val inputLanguageHintText: String
    val viewApiLogs: String
    val logsInfoTitle: String
    val logsInfoDescription: String

    // API Providers (keep English names but add description)
    val providerDescription: String

    // Voice Modes
    val voiceModes: String
    val selectMode: String
    val addVoiceMode: String
    val editVoiceMode: String
    val deleteVoiceMode: String
    val modeName: String
    val systemPrompt: String
    val enterPrompt: String

    // Default Voice Modes
    val modeVerbatim: String
    val modeVerbatimPrompt: String
    val modeFixGrammar: String
    val modeFixGrammarPrompt: String
    val modePromptFormatter: String
    val modePromptFormatterPrompt: String
    val modeLlmResponse: String
    val modeLlmResponsePrompt: String

    // Appearance Settings
    val appearanceSettings: String
    val colorScheme: String
    val useDynamicColor: String
    val useDynamicColorDesc: String
    val themeMode: String
    val darkMode: String
    val textSize: String
    val fontFamily: String
    val uiLanguage: String

    // Dark Mode Options
    val darkModeFollowSystem: String
    val darkModeAlwaysLight: String
    val darkModeAlwaysDark: String

    // Advanced Settings
    val advancedSettings: String
    val autoCopyToClipboard: String
    val autoCopyToClipboardDesc: String
    val enableHistoryPanel: String
    val enableHistoryPanelDesc: String

    // About Screen
    val aboutHyperWhisper: String
    val version: String
    val versionCode: String
    val description: String
    val features: String
    val featuresList: String
    val usageStatisticsAndCosts: String
    val noUsageDataYet: String
    val totalAudio: String
    val estimatedTotalCost: String
    val modelBreakdown: String
    val inputTokens: String
    val outputTokens: String
    val totalTokens: String
    val audioBasedPricing: String
    val costsEstimateNote: String
    val clearStatistics: String

    // Logs Screen
    val traceLogs: String
    val diagnosticLogs: String
    val diagnosticLogsDesc: String
    val logsStoredAt: String
    val noLogsYet: String
    val copyLogs: String
    val clearLogs: String
    val logsCopiedToClipboard: String

    // Configuration Info
    val currentConfiguration: String
    val provider: String
    val transcriptionModel: String
    val postProcessingModel: String
    val postProcessingModelDesc: String
    val none: String
    val keepOriginal: String
    val notConfigured: String

    // Transcription History
    val transcriptionHistory: String
    val historyCount: String  // Format: "{count}/20"
    val noHistoryYet: String
    val clearAll: String

    // Connection Test
    val connectionTesting: String
    val connectionSuccess: String
    val connectionFailed: String
    val authenticationFailed: String
    val endpointNotFound: String
    val connectionTimeout: String
    val sslError: String

    // Errors
    val error: String
    val copyError: String
    val openSettings: String
    val dismiss: String
    val errorConfigureApiKey: String
    val errorNoModeSelected: String
    val errorRecordingFailed: String
    val errorNetworkFailed: String
    val errorApiCall: String
    val errorPermissionMicrophone: String
    val errorMicrophoneInUse: String

    // Processing Info Toast
    val translated: String
    val twoStepProcessing: String

    // Time units
    val minutes: String
    val seconds: String

    // Input Field Information
    val inputFieldType: String
    val inputFieldApp: String
    val inputFieldAction: String
    val fieldTypeText: String
    val fieldTypeEmail: String
    val fieldTypePassword: String
    val fieldTypeNumber: String
    val fieldTypePhone: String
    val fieldTypeUrl: String
    val fieldTypeMultiline: String
    val fieldTypeUnknown: String
    val actionNone: String
    val actionDone: String
    val actionGo: String
    val actionSearch: String
    val actionSend: String
    val actionNext: String
    val actionPrevious: String
}
