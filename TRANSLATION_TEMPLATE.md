# HyperWhisper Translation Template

This file contains all UI strings for translation. To add a new language:

1. Copy the template below
2. Translate all values (keep the property names unchanged)
3. Create `[Language]Strings.kt` in `app/src/main/java/com/hyperwhisper/ime/localization/`
4. Add to `AppLanguage.kt` enum and `getStrings()` function

---

## Quick Start: Copy This Template

Create a new file like `SpanishStrings.kt`:

```kotlin
package com.hyperwhisper.localization

object SpanishStrings : Strings {
    // ============================================================
    // APP INFO
    // ============================================================
    override val appName = "HyperWhisper"  // Keep as-is (brand name)
    override val imeName = "HyperWhisper Voice Keyboard"  // TRANSLATE

    // ============================================================
    // COMMON ACTIONS
    // ============================================================
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val edit = "Edit"
    override val add = "Add"
    override val close = "Close"
    override val back = "Back"
    override val copy = "Copy"
    override val clear = "Clear"

    // ============================================================
    // KEYBOARD UI
    // ============================================================
    override val tapToSpeak = "Tap to speak"
    override val recording = "Recording..."
    override val processing = "Processing..."
    override val space = "space"
    override val pasteLastTranscription = "PASTE LAST (hold: history)"
    override val pasteLastHold = "PASTE LAST (hold: history)"
    override val holdForHistory = "Hold for history"

    // ============================================================
    // BUTTONS
    // ============================================================
    override val switchKeyboard = "Switch Keyboard"
    override val helpAndAbout = "Help & About"
    override val settings = "Settings"
    override val viewLogs = "View Logs"
    override val stopRecording = "Stop Recording"
    override val startRecording = "Start Recording"

    // ============================================================
    // ACCESSIBILITY DESCRIPTIONS
    // ============================================================
    override val switchKeyboardDesc = "Switch Keyboard"
    override val helpAndAboutDesc = "Help & About"
    override val settingsDesc = "Settings"
    override val viewLogsDesc = "View Logs"
    override val cancelDesc = "Cancel"
    override val enterDesc = "Enter"

    // ============================================================
    // LANGUAGE SELECTORS
    // ============================================================
    override val inputLanguageSpeech = "Input Language (Speech)"
    override val outputLanguageTranslation = "Output Language (Translation)"
    override val autoDetect = "Auto-detect"
    override val searchLanguages = "Search..."
    override val searchPlaceholder = "Search..."
    override val noLanguagesFound = "No languages found"

    // ============================================================
    // SETTINGS SCREEN
    // ============================================================
    override val settingsTitle = "HyperWhisper Settings"
    override val apiConfiguration = "API Configuration"
    override val apiProvider = "API Provider"
    override val baseUrl = "Base URL"
    override val baseUrlHint = "Must end with /"
    override val apiKey = "API Key"
    override val apiKeyPlaceholder = "sk-..."  // Keep as-is
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

    // ============================================================
    // API PROVIDERS
    // ============================================================
    override val providerDescription = "Choose your speech-to-text provider"

    // ============================================================
    // VOICE MODES
    // ============================================================
    override val voiceModes = "Voice Processing Modes"
    override val selectMode = "Select Mode"
    override val addVoiceMode = "Add Voice Mode"
    override val editVoiceMode = "Edit Voice Mode"
    override val deleteVoiceMode = "Delete Mode"
    override val modeName = "Mode Name"
    override val systemPrompt = "System Prompt"
    override val enterPrompt = "Enter the system prompt for this mode"

    // ============================================================
    // DEFAULT VOICE MODE NAMES & PROMPTS
    // ============================================================
    override val modeVerbatim = "Verbatim"
    override val modeVerbatimPrompt = "Transcribe the audio exactly as spoken."
    override val modeFixGrammar = "Fix Grammar"
    override val modeFixGrammarPrompt = "Transcribe this audio and fix any grammar, spelling, and punctuation errors while preserving the original meaning and tone."
    override val modePromptFormatter = "Prompt Formatter"
    override val modePromptFormatterPrompt = "Reformulate the user's input into a clear, effective prompt suitable for LLM processing. Enhance clarity, add necessary context, and structure it for optimal AI understanding. Maintain the user's intent while making it more precise and actionable."
    override val modeLlmResponse = "LLM Response"
    override val modeLlmResponsePrompt = "The user is asking a question. Provide a direct, concise answer to the question without any additional explanation or context. Return ONLY the answer itself."

    // ============================================================
    // APPEARANCE SETTINGS
    // ============================================================
    override val appearanceSettings = "Appearance"
    override val colorScheme = "Color Scheme"
    override val useDynamicColor = "Use Dynamic Color"
    override val useDynamicColorDesc = "Match system wallpaper colors"
    override val themeMode = "Theme Mode"
    override val darkMode = "Dark Mode"
    override val textSize = "Text Size"
    override val fontFamily = "Font Family"
    override val uiLanguage = "Interface Language"

    // ============================================================
    // DARK MODE OPTIONS
    // ============================================================
    override val darkModeFollowSystem = "Follow System"
    override val darkModeAlwaysLight = "Always Light"
    override val darkModeAlwaysDark = "Always Dark"

    // ============================================================
    // ADVANCED SETTINGS
    // ============================================================
    override val advancedSettings = "Advanced"
    override val autoCopyToClipboard = "Auto-copy to Clipboard"
    override val autoCopyToClipboardDesc = "Automatically copy transcriptions"
    override val enableHistoryPanel = "Enable History Panel"
    override val enableHistoryPanelDesc = "Long-press paste button to view history"

    // ============================================================
    // ABOUT SCREEN
    // ============================================================
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

    // ============================================================
    // LOGS SCREEN
    // ============================================================
    override val traceLogs = "Trace Logs"
    override val diagnosticLogs = "Diagnostic Logs"
    override val diagnosticLogsDesc = "These logs show API calls, processing steps, and errors. Logs are cleared on app restart. You can copy or clear them using the buttons above."
    override val logsStoredAt = "Logs are stored in"
    override val noLogsYet = "No logs yet.\nStart using the keyboard to see activity logs."
    override val copyLogs = "Copy Logs"
    override val clearLogs = "Clear Logs"
    override val logsCopiedToClipboard = "Logs copied to clipboard"

    // ============================================================
    // CONFIGURATION INFO
    // ============================================================
    override val currentConfiguration = "Current Configuration"
    override val provider = "Provider"
    override val transcriptionModel = "Transcription Model"
    override val postProcessingModel = "Post-Processing Model"
    override val postProcessingModelDesc = "gpt-4o-mini (for non-verbatim modes & translation)"
    override val none = "None"
    override val keepOriginal = "keep original"
    override val notConfigured = "Not configured"

    // ============================================================
    // TRANSCRIPTION HISTORY
    // ============================================================
    override val transcriptionHistory = "Transcription History"
    override val historyCount = "{count}/20"  // Keep format, translate if needed
    override val noHistoryYet = "No history yet"
    override val clearAll = "CLEAR ALL"

    // ============================================================
    // CONNECTION TEST RESULTS
    // ============================================================
    override val connectionTesting = "Testing connection..."
    override val connectionSuccess = "Connection successful! API is responding."
    override val connectionFailed = "Connection failed"
    override val authenticationFailed = "Authentication failed. Check your API key."
    override val endpointNotFound = "Endpoint not found. Check base URL and model ID."
    override val connectionTimeout = "Connection timeout. Check your internet connection."
    override val sslError = "SSL/TLS error. Check endpoint URL (https)."

    // ============================================================
    // ERROR MESSAGES
    // ============================================================
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

    // ============================================================
    // PROCESSING INFO
    // ============================================================
    override val translated = "Translated to"
    override val twoStepProcessing = "Two-step processing"

    // ============================================================
    // TIME UNITS (keep short)
    // ============================================================
    override val minutes = "m"
    override val seconds = "s"

    // ============================================================
    // INPUT FIELD INFORMATION
    // ============================================================
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

    // ============================================================
    // USAGE GUIDE (ABOUT SCREEN)
    // ============================================================
    override val usageGuideTitle = "How to Use"
    override val voiceModesGuide = "Voice Modes"
    override val voiceModeVerbatimDesc = "Verbatim - Exact transcription as spoken"
    override val voiceModeFixGrammarDesc = "Fix Grammar - Corrects grammar and spelling"
    override val voiceModePoliteDesc = "Polite - Makes speech professional and friendly"
    override val voiceModePromptFormatterDesc = "Prompt Formatter - Optimizes text for AI prompts"
    override val voiceModeLlmResponseDesc = "LLM Response - Get direct answers to questions"
    override val voiceModeConfigurationDesc = "Configuration - Control app settings by voice"
    override val configurationModeTitle = "Configuration Mode"
    override val configurationModeDesc = "Switch to \"Configuration\" mode to control settings by voice commands. When you speak a command, you'll see a confirmation dialog showing what will change."
    override val sampleVoiceCommandsTitle = "Sample Voice Commands"
    override val sampleCommandLanguage = "\"Change input language to Spanish\" / \"Translate to French\""
    override val sampleCommandMode = "\"Change mode to verbatim\" / \"Enable fix grammar mode\""
    override val sampleCommandTheme = "\"Switch to dark mode\" / \"Use light theme\" / \"Follow system theme\""
    override val sampleCommandHistory = "\"Enable history\" / \"Turn off history\""
    override val sampleCommandDeveloper = "\"Enable developer mode\" / \"Turn off techie mode\""
    override val sampleCommandInterface = "\"Change interface to Russian\" / \"Set UI language to Arabic\""
    override val sampleCommandExit = "\"Exit configuration mode\" / \"Turn off command mode\""
    override val howItWorksTitle = "How it works"
    override val howItWorksStep1 = "1. Select \"Configuration\" from the mode selector"
    override val howItWorksStep2 = "2. Tap the microphone and speak your command"
    override val howItWorksStep3 = "3. Review the change in the confirmation dialog"
    override val howItWorksStep4 = "4. Tap \"Apply Change\" to confirm or \"Cancel\" to dismiss"
    override val howItWorksConfirmation = "After confirmation, you'll see a notification that the setting was updated."
    override val deleteButtonTitle = "Delete Button"
    override val deleteButtonTap = "Tap - Delete one character (or selected text)"
    override val deleteButtonHold = "Hold - Repeat delete continuously"
    override val deleteButtonLongHold = "Hold 5 seconds - Delete ALL text (button turns red)"
    override val languageSelectionTitle = "Language Selection"
    override val languageSelectionDesc = "Use Configuration mode to change languages hands-free, or tap the language buttons below the microphone to select from the list."
}
```

---

## Step-by-Step: Adding a New Language

### 1. Create the Strings File

Copy the template above to:
```
app/src/main/java/com/hyperwhisper/ime/localization/[Language]Strings.kt
```

Example: `SpanishStrings.kt`, `FrenchStrings.kt`, `GermanStrings.kt`

### 2. Update AppLanguage.kt

Add your language to the enum:

```kotlin
enum class AppLanguage(
    val displayName: String,
    val nativeName: String,
    val code: String,
    val isRTL: Boolean = false
) {
    ENGLISH("English", "English", "en", false),
    RUSSIAN("Russian", "Русский", "ru", false),
    ARABIC("Arabic", "العربية", "ar", true),
    SPANISH("Spanish", "Español", "es", false),  // ADD THIS
    // ... more languages
}
```

### 3. Update getStrings() Function

Add the mapping in `AppLanguage.kt`:

```kotlin
fun AppLanguage.getStrings(): Strings {
    return when (this) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.RUSSIAN -> RussianStrings
        AppLanguage.ARABIC -> ArabicStrings
        AppLanguage.SPANISH -> SpanishStrings  // ADD THIS
    }
}
```

### 4. Build and Test

```bash
./build-android.sh
```

---

## Translation Notes

### Keep These Unchanged
- `appName` = "HyperWhisper" (brand name)
- `apiKeyPlaceholder` = "sk-..." (technical format)
- `historyCount` = "{count}/20" (format string)
- Technical terms like URLs, API, SSL, etc.

### RTL Languages
For Right-to-Left languages (Arabic, Hebrew, Farsi):
- Set `isRTL = true` in the enum
- UI will automatically mirror

### Long Strings
Some languages have longer words. Test these carefully:
- Button labels
- Settings titles
- Error messages

### Special Characters
Ensure proper encoding for:
- Cyrillic (Russian)
- Arabic script
- Chinese/Japanese characters
- Accented Latin (French, German, Spanish)

---

## String Count Summary

Total strings to translate: **120**

| Category | Count |
|----------|-------|
| App Info | 2 |
| Common Actions | 9 |
| Keyboard UI | 7 |
| Buttons | 6 |
| Accessibility | 6 |
| Language Selectors | 6 |
| Settings | 15 |
| Voice Modes | 13 |
| Appearance | 11 |
| About Screen | 17 |
| Logs | 10 |
| Configuration | 8 |
| History | 4 |
| Connection Test | 7 |
| Errors | 12 |
| Processing | 2 |
| Time Units | 2 |
| Input Field Info | 17 |
| Usage Guide | 25 |

---

## Quick Reference: Common Translations

| English | Spanish | French | German |
|---------|---------|--------|--------|
| Cancel | Cancelar | Annuler | Abbrechen |
| Save | Guardar | Enregistrer | Speichern |
| Delete | Eliminar | Supprimer | Löschen |
| Settings | Configuración | Paramètres | Einstellungen |
| Error | Error | Erreur | Fehler |
| Recording... | Grabando... | Enregistrement... | Aufnahme... |
| Processing... | Procesando... | Traitement... | Verarbeitung... |

---

## Validation Checklist

Before submitting a translation:

- [ ] All 120 strings translated
- [ ] No English left (except brand names)
- [ ] Special characters display correctly
- [ ] Long strings don't break UI
- [ ] RTL layout works (if applicable)
- [ ] Tested on actual device
