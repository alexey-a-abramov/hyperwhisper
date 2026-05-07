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

    // Walkie-Talkie Mode
    val walkieTalkieModeEnabled: String  // "Walkie-Talkie mode enabled. To exit, double-tap the button."
    val normalModeEnabled: String  // "Normal mode enabled. Long press to activate Walkie-Talkie mode."
    val walkieTalkieLongPressHint: String  // "Long press for Walkie-Talkie mode"

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

    // Usage Guide (About Screen)
    val usageGuideTitle: String
    val voiceModesGuide: String
    val voiceModeVerbatimDesc: String
    val voiceModeFixGrammarDesc: String
    val voiceModePoliteDesc: String
    val voiceModePromptFormatterDesc: String
    val voiceModeLlmResponseDesc: String
    val voiceModeConfigurationDesc: String
    val configurationModeTitle: String
    val configurationModeDesc: String
    val sampleVoiceCommandsTitle: String
    val sampleCommandLanguage: String
    val sampleCommandMode: String
    val sampleCommandTheme: String
    val sampleCommandHistory: String
    val sampleCommandDeveloper: String
    val sampleCommandInterface: String
    val sampleCommandExit: String
    val howItWorksTitle: String
    val howItWorksStep1: String
    val howItWorksStep2: String
    val howItWorksStep3: String
    val howItWorksStep4: String
    val howItWorksConfirmation: String
    val deleteButtonTitle: String
    val deleteButtonTap: String
    val deleteButtonHold: String
    val deleteButtonLongHold: String
    val languageSelectionTitle: String
    val languageSelectionDesc: String

    // Settings home — category tile titles and subtitles. Surfaced in
    // SettingsHomeScreen + the Settings detail TopAppBar.
    val categoryTranscriptionTitle: String
    val categoryTranscriptionSubtitle: String
    val categoryPostProcessingTitle: String
    val categoryPostProcessingSubtitle: String
    val categoryLocalModelsTitle: String
    val categoryLocalModelsSubtitle: String
    val categoryVoiceModesTitle: String
    val categoryVoiceModesSubtitle: String
    val categoryKeyboardBehaviorTitle: String
    val categoryKeyboardBehaviorSubtitle: String
    val categoryAppearanceTitle: String
    val categoryAppearanceSubtitle: String
    val categoryAdvancedTitle: String
    val categoryAdvancedSubtitle: String
    val categoryAboutTitle: String
    val categoryAboutSubtitle: String

    // About — new stats labels (audio + text + typing-time-saved).
    val statsAudioTranscribedLabel: String
    val statsTextWrittenLabel: String
    val statsTypingTimeSavedLabel: String

    // Settings overflow menu, top bar misc, advanced detail rows, toasts.
    val settingsOverflowAbout: String
    val settingsOverflowExportSecrets: String
    val settingsOverflowMoreDesc: String
    val settingsActivePrefix: String  // e.g. "Active: " (with trailing space)
    val settingsSecretsCopiedToast: String
    val advancedApiLogsTitle: String
    val advancedApiLogsDescription: String
    val advancedProviderKeyHelpTitle: String
    val advancedProviderKeyHelpDescription: String
    val advancedExportSecretsTitle: String
    val advancedExportSecretsDescription: String

    // Appearance section group headers + toggle labels.
    val appearanceSectionColorHeader: String
    val appearanceDynamicColorTitle: String
    val appearanceDynamicColorDescription: String
    val appearanceSectionThemeHeader: String
    val appearanceSectionTypographyHeader: String
    val appearanceTextSizeLabel: String
    val appearanceFontFamilyLabel: String
    val appearanceSectionInterfaceLanguageHeader: String

    // LLM config section.
    val llmLocalLlamacppHeader: String
    val llmLocalLlamacppDescription: String
    val llmLocalLlamacppCommands: String
    val llmLocalLlamacppModelHint: String
    val llmLocalGemmaHeader: String
    val llmLocalGemmaActiveModelPrefix: String  // "Model: " — concatenated with file name
    val llmLocalGemmaNoModelHint: String
    val llmLocalGemmaOnDeviceNote: String
    val llmConfigBaseUrlLabel: String
    val llmConfigBaseUrlSupportingText: String
    val llmConfigNoApiKeyRequired: String
    val llmConfigApiKeyLabel: String
    val llmConfigApiKeyPlaceholder: String
    val llmConfigUseTranscriptionKey: String
    val llmConfigReuseKeyForTranscription: String
    val llmConfigModelInfoDesc: String
    val llmConfigUsageInfo: String
    val llmConfigTestPostProcessing: String
    val llmConfigTestRunningPlaceholder: String
    val llmConfigDisabledNote: String

    // Keyboard behavior section.
    val keyboardBehaviorHistoryHeader: String
    val keyboardBehaviorEnableHistoryDescription: String
    val keyboardBehaviorUnlimitedHistoryTitle: String
    val keyboardBehaviorUnlimitedHistoryDescription: String
    val keyboardBehaviorClipboardAudioHeader: String
    val keyboardBehaviorAutoCopyDescription: String
    val keyboardBehaviorSaveAudioTitle: String
    val keyboardBehaviorSaveAudioDescription: String
    val keyboardBehaviorKeyboardHeader: String
    val keyboardBehaviorShowSwitcherTitle: String
    val keyboardBehaviorShowSwitcherDescription: String
    val keyboardBehaviorPerAppLayoutTitle: String
    val keyboardBehaviorPerAppLayoutDescription: String
    val keyboardBehaviorCodingAgentsHeader: String
    val keyboardBehaviorCodingAgentsDescription: String
    val keyboardBehaviorAgentDescriptionPrefix: String  // "Quick-command keyboard for " (concatenated with displayName)
    val keyboardBehaviorDeveloperHeader: String
    val keyboardBehaviorTechieModeTitle: String
    val keyboardBehaviorTechieModeDescription: String
    val keyboardBehaviorMaxHistoryTitle: String
    val keyboardBehaviorMaxHistoryRange: String

    // Selectors (font, dark mode, UI language, model, LLM model, providers).
    val selectorFontLabel: String
    val selectorLanguageLabel: String
    val selectorCustomModelTag: String
    val selectorFilterModelsLabel: String
    val selectorFreeFilterLabel: String
    val selectorNoMatchingModels: String
    val selectorLlmModelLabel: String
    val selectorLlmProviderLabel: String
    val selectorProviderLabel: String

    // Transcription section — Cloud/Local pivot, test panel, field groups.
    val transcriptionTabCloud: String
    val transcriptionTabLocal: String
    val transcriptionTestButton: String
    val transcriptionTestingShort: String
    val transcriptionPreparingTest: String
    val transcriptionWorkingPlaceholder: String
    val transcriptionTestSuccessLabel: String
    val transcriptionTestFailedLabel: String
    val transcriptionTestReadyLabel: String
    val transcriptionTestLogCopied: String
    val transcriptionTestLogCopyDesc: String
    val transcriptionTestLogDismissDesc: String
    val transcriptionTestLogExpand: String
    val transcriptionTestLogCollapse: String
    val transcriptionCloudActiveText: String
    val transcriptionCloudInactiveText: String
    val transcriptionSetCloudActive: String
    val transcriptionFieldGroupModel: String
    val transcriptionApiKeyRequiredHint: String
    val transcriptionApiKeyOptionalHint: String
    val transcriptionApiKeyHide: String
    val transcriptionApiKeyShow: String
    val transcriptionHowToGetKey: String
    val transcriptionLanguagesHeader: String
    val transcriptionInputLanguageLabel: String
    val transcriptionInputLanguageSupporting: String
    val transcriptionOutputLanguageLabel: String
    val transcriptionOutputLanguageSupporting: String
    val transcriptionApiEndpointLabel: String
    val transcriptionRequiresApiKey: String
    val transcriptionRequiresApiKeyDesc: String
    val transcriptionLocalActivePrefix: String  // "On-device Whisper is active: " (concat with file name)
    val transcriptionLocalInactiveText: String
    val transcriptionStorageAccessRequired: String
    val transcriptionStorageAccessDesc: String
    val transcriptionGrantAccess: String
    val transcriptionWhisperModelsHeader: String
    val transcriptionNoModelsFound: String
    val transcriptionRescan: String
    val transcriptionRefreshAccess: String
    val transcriptionPerformanceHeader: String
    val transcriptionAutoDiscoverTitle: String
    val transcriptionAutoDiscoverDesc: String
    val transcriptionThreadsTitle: String
    val transcriptionThreadsDesc: String
    val transcriptionVerifyIntegrityDesc: String
    val transcriptionUseThisModel: String
    val transcriptionActiveBadge: String

    // Voice mode dialogs — placeholders.
    val dialogVoiceModeNamePlaceholder: String
    val dialogVoiceModePromptPlaceholder: String

    // Language info dialog body.
    val dialogInputLanguageBody: String
    val dialogInputLanguageBullet1: String
    val dialogInputLanguageBullet2: String
    val dialogInputLanguageBullet3: String
    val dialogInputLanguageFooter: String

    // Logs info dialog body.
    val dialogLogsInfoBody: String
    val dialogLogsInfoViewingHeader: String
    val dialogLogsInfoViewing1: String
    val dialogLogsInfoViewing2: String
    val dialogLogsInfoViewing3: String
    val dialogLogsInfoLoggedHeader: String
    val dialogLogsInfoLogged1: String
    val dialogLogsInfoLogged2: String
    val dialogLogsInfoLogged3: String
    val dialogLogsInfoLogged4: String
    val dialogLogsInfoLogged5: String
    val dialogLogsInfoNote: String

    // Model info dialog — structural strings only.
    val dialogModelInfoTitle: String
    val dialogModelInfoProviderPrefix: String  // "Provider: "
    val dialogModelInfoSelectedModelLabel: String
    val dialogModelInfoAvailableModelsLabel: String

    // Provider key instructions dialog — title + per-provider steps.
    val dialogProviderKeyTitle: String
    val providerKeyOpenaiStep1: String
    val providerKeyOpenaiStep2: String
    val providerKeyOpenaiStep3: String
    val providerKeyDeepgramStep1: String
    val providerKeyDeepgramStep2: String
    val providerKeyDeepgramStep3: String
    val providerKeyAssemblyStep1: String
    val providerKeyAssemblyStep2: String
    val providerKeyAssemblyStep3: String
    val providerKeyGoogleCloudStep1: String
    val providerKeyGoogleCloudStep2: String
    val providerKeyGoogleCloudStep3: String
    val providerKeyAwsStep1: String
    val providerKeyAwsStep2: String
    val providerKeyAwsStep3: String
    val providerKeyAzureStep1: String
    val providerKeyAzureStep2: String
    val providerKeyAzureStep3: String
    val providerKeyDeepseekStep1: String
    val providerKeyDeepseekStep2: String
    val providerKeyDeepseekStep3: String
    val providerKeyMistralStep1: String
    val providerKeyMistralStep2: String
    val providerKeyMistralStep3: String
    val providerKeyRevStep1: String
    val providerKeyRevStep2: String
    val providerKeyRevStep3: String
    val providerKeyGroqStep1: String
    val providerKeyGroqStep2: String
    val providerKeyGroqStep3: String
    val providerKeyOpenrouterStep1: String
    val providerKeyOpenrouterStep2: String
    val providerKeyOpenrouterStep3: String
    val providerKeyGeminiStep1: String
    val providerKeyGeminiStep2: String
    val providerKeyGeminiStep3: String
    val providerKeyAntigravityStep1: String
    val providerKeyAntigravityStep2: String
    val providerKeyAntigravityStep3: String
    val providerKeyHuggingfaceStep1: String
    val providerKeyHuggingfaceStep2: String
    val providerKeyHuggingfaceStep3: String
    val providerKeySelfhostedStep1: String
    val providerKeySelfhostedStep2: String
    val providerKeySelfhostedStep3: String
    val providerKeyLocalStep1: String
    val providerKeyLocalStep2: String
    val providerKeyLocalStep3: String

    // History reduction warning dialog.
    val dialogHistoryReductionTitle: String
    val dialogHistoryReductionBodyFormat: String  // "You are reducing... from %1$d to %2$d items."
    val dialogHistoryReductionWarningFormat: String  // "%1$d history items including their audio files will be permanently deleted."
    val dialogHistoryReductionUndoNote: String  // "This action cannot be undone."
    val dialogHistoryReductionConfirmFormat: String  // "DELETE %1$d ITEMS"

    // API call logs screen.
    val apiCallLogsHeader: String
    val apiCallLogsSubtitle: String
    val apiCallLogsStatistics: String
    val apiCallLogsStatTotal: String
    val apiCallLogsStatSuccess: String
    val apiCallLogsStatErrors: String
    val apiCallLogsStatAvgTime: String
    val apiCallLogsEmpty: String
    val apiCallLogsErrorPrefix: String  // "Error: "
    val apiCallLogsSuccessDesc: String  // accessibility
    val apiCallLogsErrorDesc: String  // accessibility

    // Language selector placeholder.
    val languageSelectorSearchPlaceholder: String

    // Keyboard shared — accessibility & top strip.
    val keyboardDictationDesc: String
    val keyboardBackspaceDesc: String
    val keyboardEnterDesc: String
    val keyboardSearchDesc: String
    val keyboardClearDesc: String
    val keyboardBackToVoiceDesc: String
    val keyboardLogsDesc: String
    val keyboardHelpDesc: String

    // Emoji keyboard.
    val emojiNoEmojisFound: String
    val emojiRecentlyUsed: String
    val emojiSearchPlaceholder: String

    // Enum: LlmProvider displayName
    val providerLlmNone: String
    val providerLlmOpenai: String
    val providerLlmDeepseek: String
    val providerLlmGemini: String
    val providerLlmAnthropic: String
    val providerLlmMistral: String
    val providerLlmGroq: String
    val providerLlmOpenrouter: String
    val providerLlmOpenaiCompatible: String
    val providerLlmLocalGemma: String
    val providerLlmLocalLlamacpp: String

    // Enum: ApiProvider displayName
    val providerApiOpenai: String
    val providerApiDeepgram: String
    val providerApiAssemblyai: String
    val providerApiGoogleCloud: String
    val providerApiAwsTranscribe: String
    val providerApiAzureSpeech: String
    val providerApiDeepseek: String
    val providerApiMistral: String
    val providerApiRevai: String
    val providerApiGroq: String
    val providerApiOpenrouter: String
    val providerApiGemini: String
    val providerApiAntigravity: String
    val providerApiHuggingface: String
    val providerApiSelfhostedWhisper: String
    val providerApiLocalWhisper: String

    // Enum: ProcessingStage displayName
    val processingStagePreparing: String
    val processingStageConvertingAudio: String
    val processingStageLoadingModel: String
    val processingStageTranscribing: String
    val processingStagePostProcessing: String
    val processingStageUploading: String
    val processingStageWaitingApi: String
    val processingStageFinishing: String

    // Enum: ColorSchemeOption displayName
    val themeTerminalDark: String
    val themeOceanDeep: String
    val themeForestNight: String
    val themeSunsetHorizon: String
    val themeArcticFrost: String
    val themeDesertStorm: String
    val themeNeonCity: String
    val themeCherryBlossom: String
    val themeMidnightSky: String
    val themeLavaFlow: String
    val themeMistyMountain: String
    val themeAutumnLeaves: String
    val themeProfessionalBlue: String
    val themeWarmEarth: String
    val themeCoolSlate: String
    val themeVibrantPurple: String
    val themeEmeraldGreen: String
    val themeRubyRed: String

    // Enum: UIScaleOption displayName
    val uiScaleVerySmall: String
    val uiScaleSmall: String
    val uiScaleMedium: String
    val uiScaleLarge: String
    val uiScaleVeryLarge: String

    // Enum: FontFamilyOption displayName
    val fontFamilyDefault: String
    val fontFamilySerif: String
    val fontFamilySansSerif: String
    val fontFamilyMonospace: String
    val fontFamilyCursive: String

    // Enum: KeyboardInputMode displayName
    val keyboardModeDictation: String
    val keyboardModeQwerty: String
    val keyboardModeCode: String
    val keyboardModeEmoji: String
    val keyboardModeAgentClaudeCode: String
    val keyboardModeAgentOpencode: String
    val keyboardModeAgentGemini: String
    val keyboardModeAgentCodex: String
    val keyboardModeAgentMacros: String
    val keyboardModeSpecialChars: String
    val keyboardModeSystemKeys: String
    val keyboardModeVibeCoding: String
    val keyboardModeNumpad: String

    // Enum: KeyboardLayout displayName
    val keyboardLayoutEnglish: String
    val keyboardLayoutRussian: String
    val keyboardLayoutSpanish: String
    val keyboardLayoutFrench: String
    val keyboardLayoutGerman: String
    val keyboardLayoutArabic: String

    // Enum: EmojiCategory displayName
    val emojiCategorySmileys: String
    val emojiCategoryPeople: String
    val emojiCategoryAnimals: String
    val emojiCategoryFood: String
    val emojiCategoryActivities: String
    val emojiCategoryTravel: String
    val emojiCategoryObjects: String
    val emojiCategorySymbols: String
    val emojiCategoryFlags: String

    // Processing indicator hint shown next to the spinner during transcription.
    // Format string: %d is the estimated remaining seconds.
    val processingEstimatedTimeFormat: String  // "Est: ~%ds"

    // Network error messages — surfaced to the user from non-Composable code
    // (VoiceRepository, ConnectionTester) via stringsFor(languageCode).
    val errorForbidden: String                     // "Forbidden — verify key permissions."
    val errorRateLimit: String                     // "Rate limit exceeded — try again shortly."
    val errorUnknown: String                       // "Unknown error"
    val errorProcessingFailedFormat: String        // "Processing failed: %1$s: %2$s"

    // Plain-language summaries for the error overlay (one per ErrorKind).
    // Format arg %1$s is the provider's display name, or
    // [errorOverlayProviderFallback] when no provider is known.
    val errorOverlayProviderFallback: String       // "the provider"
    val errorOverlaySummaryAuthFormat: String           // "%s rejected the API key…"
    val errorOverlaySummaryModelNotFoundFormat: String  // "%s doesn't recognise the configured model…"
    val errorOverlaySummaryRateLimitedFormat: String    // "%s is rate-limiting requests…"
    val errorOverlaySummaryTimeoutFormat: String        // "The request to %s timed out…"
    val errorOverlaySummaryProviderDownFormat: String   // "%s returned a server error…"
    val errorOverlaySummaryNetworkFormat: String        // "Couldn't reach %s. Check your network…"
    val errorOverlaySummaryUnknownFormat: String        // "Something went wrong while talking to %s…"
    val errorOverlaySwitchProvider: String              // "Switch provider"

    // Biometric / device-credential gate shown before revealing or exporting
    // API keys. Title is reused for export ("Confirm to reveal/export"),
    // subtitle is the explanatory body. NotEnrolled fires when the device has
    // no PIN/pattern/password/biometric configured.
    val secretsGateRevealTitle: String           // "Confirm to reveal API key"
    val secretsGateExportTitle: String           // "Confirm to export secrets"
    val secretsGateSubtitle: String              // "Use your device lock to continue"
    val secretsGateNotEnrolledMessage: String    // "Set up a device lock to continue"
    val secretsGateUnavailableMessage: String    // "Device authentication unavailable"

    // On-device transcription / inference errors.
    val errorLocalWhisperPathMissing: String                 // "Local Whisper model path is not configured."
    val errorLocalWhisperModelNotFoundFormat: String         // "Local Whisper model not found at: %s"
    val errorLocalWhisperEmptyResult: String                 // "On-device Whisper returned no text. ..."
    val errorLocalProcessingFailedFormat: String             // "Local processing failed: %1$s: %2$s"
    val errorLocalInferenceFailedFormat: String              // "Local inference failed: %1$s: %2$s"
}
