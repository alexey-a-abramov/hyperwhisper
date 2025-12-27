# HyperWhisper Internationalization & RTL Support Refactoring Plan

## Executive Summary

This document outlines a comprehensive refactoring strategy to:
1. Complete migration of all UI strings to the existing localization system
2. Add Right-to-Left (RTL) language support
3. Implement an input field information display component
4. Improve overall translation workflow

## Current State Analysis

### ✅ What's Working Well

- **Solid Foundation**: Custom Kotlin-based i18n framework with 170+ string properties
- **Type Safety**: Interface-based design with compile-time checking
- **Runtime Switching**: Language can change without app restart
- **Documentation**: Comprehensive LOCALIZATION.md guide
- **Complete Translations**: English and Russian fully implemented
- **Proper Integration**: Uses Compose CompositionLocal pattern

### ❌ Current Issues

1. **Incomplete Migration**: 50+ hardcoded strings in UI components
   - KeyboardScreen.kt: ~20 hardcoded strings
   - SettingsScreen.kt: ~40 hardcoded strings

2. **No RTL Support**: Languages like Arabic, Hebrew, Farsi cannot be properly supported

3. **Missing Input Field Context**: Users don't see what type of field they're typing into

4. **Legacy Resources**: Unused strings.xml file creates confusion

## Refactoring Strategy

### Phase 1: String Property Additions (Priority: HIGH)

Add missing string properties to `Strings.kt` interface:

```kotlin
// Input Field Info (NEW)
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
val actionNone: String
val actionDone: String
val actionGo: String
val actionSearch: String
val actionSend: String
val actionNext: String

// Missing UI strings
val switchKeyboardDesc: String      // ContentDescription for accessibility
val viewLogsDesc: String
val helpAndAboutDesc: String
val settingsDesc: String
val cancelDesc: String
val errorLabel: String               // "Error" heading
val searchPlaceholder: String        // "Search..."
val noLanguagesFound: String         // Already exists
val transcriptionHistoryTitle: String
val historyCount: String            // Format: "{count}/20"
val baseUrlHint: String             // "Must end with /"
val apiKeyPlaceholder: String       // "sk-..."
val testingConnection: String
val inputLanguageHint: String
val viewApiLogs: String
val logsDescription: String
val provider: String                // "Provider" label
val transcriptionModelLabel: String
val postProcessingModelLabel: String
val pasteLastHold: String          // "PASTE LAST (hold: history)"
```

### Phase 2: Input Field Information Display (Priority: HIGH)

**Component Design:**

```kotlin
@Composable
fun InputFieldInfo(
    editorInfo: EditorInfo?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    if (editorInfo == null) return

    OutlinedCard(
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Line 1: Input type
            Text(
                text = "${strings.inputFieldType}: ${getInputTypeLabel(editorInfo.inputType, strings)}",
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 2: IME Action
            Text(
                text = "${strings.inputFieldAction}: ${getImeActionLabel(editorInfo.imeOptions, strings)}",
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 3: Package name (if available)
            editorInfo.packageName?.let { pkg ->
                Text(
                    text = "${strings.inputFieldApp}: ${pkg.substringAfterLast('.')}",
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Line 4: Hint text (if available)
            editorInfo.hintText?.let { hint ->
                Text(
                    text = hint.toString(),
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

fun getInputTypeLabel(inputType: Int, strings: Strings): String {
    val typeClass = inputType and InputType.TYPE_MASK_CLASS
    val typeVariation = inputType and InputType.TYPE_MASK_VARIATION

    return when (typeClass) {
        InputType.TYPE_CLASS_TEXT -> {
            when (typeVariation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> strings.fieldTypeEmail
                InputType.TYPE_TEXT_VARIATION_PASSWORD -> strings.fieldTypePassword
                InputType.TYPE_TEXT_VARIATION_URI -> strings.fieldTypeUrl
                else -> if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
                    strings.fieldTypeMultiline
                } else {
                    strings.fieldTypeText
                }
            }
        }
        InputType.TYPE_CLASS_NUMBER -> strings.fieldTypeNumber
        InputType.TYPE_CLASS_PHONE -> strings.fieldTypePhone
        else -> strings.fieldTypeText
    }
}

fun getImeActionLabel(imeOptions: Int, strings: Strings): String {
    return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_DONE -> strings.actionDone
        EditorInfo.IME_ACTION_GO -> strings.actionGo
        EditorInfo.IME_ACTION_SEARCH -> strings.actionSearch
        EditorInfo.IME_ACTION_SEND -> strings.actionSend
        EditorInfo.IME_ACTION_NEXT -> strings.actionNext
        else -> strings.actionNone
    }
}
```

**Integration into KeyboardScreen.kt:**

Replace lines 284-314 (cancel button box) with:

```kotlin
// Far left: Input field info OR Cancel button
Box(
    modifier = Modifier.width(80.dp),
    contentAlignment = Alignment.CenterStart
) {
    when (recordingState) {
        RecordingState.RECORDING -> {
            // Show cancel button during recording
            OutlinedButton(
                onClick = { viewModel.cancelRecording() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = strings.cancelDesc,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        strings.cancel.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        else -> {
            // Show input field info when not recording
            InputFieldInfo(
                editorInfo = editorInfo,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

**Service Changes:**

Update `VoiceInputMethodService.kt` to pass EditorInfo:

```kotlin
// In KeyboardContent()
KeyboardScreen(
    viewModel = viewModel,
    editorInfo = currentEditorInfo,  // ADD THIS
    onTextCommit = { text -> commitText(text) },
    // ... rest of parameters
)

// Update KeyboardScreen signature
@Composable
fun KeyboardScreen(
    viewModel: KeyboardViewModel,
    editorInfo: EditorInfo? = null,  // ADD THIS
    onTextCommit: (String) -> Unit,
    // ... rest of parameters
)
```

### Phase 3: RTL Layout Support (Priority: MEDIUM)

**Implementation Strategy:**

1. **Use Compose's LayoutDirection:**

```kotlin
// In Theme.kt, wrap content with layout direction based on language
@Composable
fun HyperWhisperTheme(
    appearanceSettings: AppearanceSettings,
    content: @Composable () -> Unit
) {
    val language = getLanguageByCode(appearanceSettings.uiLanguage)
    val strings = language.getStrings()
    val layoutDirection = if (language.isRTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
```

2. **Add RTL property to AppLanguage:**

```kotlin
// In AppLanguage.kt
enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val isRTL: Boolean = false  // NEW
) {
    ENGLISH("en", "English", isRTL = false),
    RUSSIAN("ru", "Русский", isRTL = false),
    ARABIC("ar", "العربية", isRTL = true),    // Future
    HEBREW("he", "עברית", isRTL = true),      // Future
    FARSI("fa", "فارسی", isRTL = true);       // Future

    fun getStrings(): Strings = when (this) {
        ENGLISH -> EnglishStrings
        RUSSIAN -> RussianStrings
        ARABIC -> ArabicStrings   // Future
        HEBREW -> HebrewStrings   // Future
        FARSI -> FarsiStrings     // Future
    }
}
```

3. **Test with BiDi-aware components:**
   - Most Compose components handle RTL automatically
   - Row -> content flows right-to-left
   - Text -> aligns to the right
   - Icons may need mirroring for directional actions

4. **Manual adjustments needed:**
   - Icons with directional meaning (arrows, chevrons)
   - Custom layouts that use absolute positioning
   - Padding/margin that should mirror

### Phase 4: String Migration (Priority: HIGH)

**Migration Checklist:**

1. **KeyboardScreen.kt** (lines to update):
   - 148: `"Switch Keyboard"` -> `strings.switchKeyboardDesc`
   - 175: `"View Logs"` -> `strings.viewLogsDesc`
   - 190: `"Help & About"` -> `strings.helpAndAboutDesc`
   - 204: `"Settings"` -> `strings.settingsDesc`
   - 307: `"CANCEL"` -> `strings.cancel`
   - 425: `"PASTE LAST (hold: history)"` -> `strings.pasteLastHold`
   - 458: `"space"` -> `strings.space`
   - 775: `"Error"` -> `strings.error`
   - 816: `"COPY ERROR"` -> `strings.copyError`
   - 846: `"OPEN SETTINGS"` -> `strings.openSettings`
   - 862: `"DISMISS"` -> `strings.dismiss`
   - 911: `"Current Configuration"` -> `strings.currentConfiguration`
   - 929: `"Provider"` -> `strings.provider`
   - 1192: `"Search..."` -> `strings.searchPlaceholder`
   - 1248: `"No languages found"` -> `strings.noLanguagesFound` (already exists!)
   - 1351: `"Transcription History"` -> `strings.transcriptionHistory`
   - 1372: `"No history yet"` -> `strings.noHistoryYet` (already exists!)

2. **SettingsScreen.kt** (major sections):
   - API Configuration section
   - Voice Modes section
   - Appearance section
   - Logs section
   - All button labels and placeholders

**Migration Script Approach:**

Create a helper script to find all hardcoded strings:

```bash
# Find potential hardcoded strings in Composables
grep -n 'Text("' app/src/main/java/com/hyperwhisper/ime/ui/*.kt
grep -n 'label = { Text("' app/src/main/java/com/hyperwhisper/ime/ui/**/*.kt
grep -n 'contentDescription = "' app/src/main/java/com/hyperwhisper/ime/ui/**/*.kt
```

### Phase 5: Translation Workflow Improvements (Priority: LOW)

**Recommendations:**

1. **Add translation status tracking:**

```kotlin
// In Strings.kt
/**
 * Translation Coverage:
 * - English: 100% ✅
 * - Russian: 100% ✅
 * - Arabic: 0% ⏳ (planned)
 * - Hebrew: 0% ⏳ (planned)
 *
 * Last updated: 2025-01-XX
 */
```

2. **Create translation template generator:**

```kotlin
// Script to generate template for new languages
fun generateTranslationTemplate(language: String) {
    // Generates a skeleton file with all properties
    // and English text as comments for reference
}
```

3. **Consider future automation:**
   - Machine translation for initial draft (with human review required)
   - Translation memory for common UI patterns
   - Glossary for technical terms

### Phase 6: Resource Cleanup (Priority: LOW)

**Actions:**

1. **strings.xml consolidation:**
   - Keep only Android-required strings (app name, permissions)
   - Remove all UI strings that are now in Kotlin
   - Add comment explaining the dual system

2. **Documentation updates:**
   - Update LOCALIZATION.md with RTL support
   - Add input field info component docs
   - Create migration guide from strings.xml to Kotlin strings

## Implementation Priority

### Must-Have (Week 1):
1. ✅ Add all missing string properties to Strings interface
2. ✅ Implement InputFieldInfo component
3. ✅ Pass EditorInfo from Service to KeyboardScreen
4. ✅ Migrate KeyboardScreen.kt hardcoded strings

### Should-Have (Week 2):
5. ⏳ Migrate SettingsScreen.kt hardcoded strings
6. ⏳ Add RTL layout direction support
7. ⏳ Test with screen reader (accessibility)

### Nice-to-Have (Week 3):
8. ⏳ Add one RTL language (Arabic recommended)
9. ⏳ Clean up strings.xml
10. ⏳ Create translation workflow tooling

## Testing Strategy

### Manual Testing:

1. **Language Switching:**
   - Switch between English and Russian
   - Verify all UI updates immediately
   - Check that no hardcoded strings remain

2. **RTL Layout:**
   - Enable RTL language
   - Verify layout mirrors correctly
   - Check text alignment and reading order
   - Test icons that need mirroring

3. **Input Field Info:**
   - Test in different apps (messaging, browser, notes)
   - Verify correct input type detection
   - Check all 4 lines display properly
   - Ensure text doesn't overflow

4. **Accessibility:**
   - Test with TalkBack screen reader
   - Verify contentDescription strings
   - Check focus order in RTL mode

### Automated Testing:

```kotlin
@Test
fun `all UI strings use LocalStrings`() {
    // Scan source files for Text("hardcoded") pattern
    // Fail if any found
}

@Test
fun `all languages have complete translations`() {
    val englishProperties = EnglishStrings::class.memberProperties
    val russianProperties = RussianStrings::class.memberProperties
    assertEquals(englishProperties.size, russianProperties.size)
}

@Test
fun `RTL languages apply correct layout direction`() {
    val arabicLanguage = AppLanguage.ARABIC
    assertTrue(arabicLanguage.isRTL)
}
```

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing translations | High | Thorough testing with both languages |
| RTL layout issues | Medium | Start with one RTL language, iterate |
| Missing edge cases in input types | Low | Comprehensive EditorInfo testing |
| Performance impact of context reading | Very Low | EditorInfo is lightweight |

## Success Metrics

- ✅ Zero hardcoded strings in UI components
- ✅ All existing features work in both LTR and RTL modes
- ✅ Input field context visible to users
- ✅ Easy to add new languages (< 1 hour per language)
- ✅ Accessibility score improves (TalkBack compatibility)

## Future Enhancements

1. **Pluralization Support:**
   ```kotlin
   fun minutes(count: Int): String
   fun itemsSelected(count: Int): String
   ```

2. **String Formatting:**
   ```kotlin
   fun costEstimate(amount: Double, currency: String): String
   fun recordingDuration(minutes: Int, seconds: Int): String
   ```

3. **Date/Time Localization:**
   ```kotlin
   val dateFormatter: DateFormat
   val timeFormatter: TimeFormat
   ```

4. **Context-aware translations:**
   ```kotlin
   // Same English word, different translations based on context
   val saveButton: String        // "Save" (button)
   val saveAction: String        // "Save" (menu item)
   val savePrompt: String        // "save your work"
   ```

## Conclusion

This refactoring will:
- Complete the excellent i18n foundation already in place
- Enable RTL language support for global accessibility
- Improve user experience with input field context
- Make future translations trivial to add

The existing localization system is well-designed; this plan simply completes the migration and adds the missing RTL support piece.
