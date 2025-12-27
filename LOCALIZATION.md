# HyperWhisper Multi-Language UI Implementation

## 📚 Overview

HyperWhisper now supports multiple UI languages with an easy-to-extend system. The implementation uses a type-safe, Compose-friendly approach that allows runtime language switching without app restart.

## 🏗️ Architecture

```
app/src/main/java/com/hyperwhisper/ime/localization/
├── Strings.kt              # Interface defining 220+ UI string properties
├── EnglishStrings.kt       # English implementation
├── RussianStrings.kt       # Russian implementation
├── ArabicStrings.kt        # Arabic implementation (RTL)
└── AppLanguage.kt          # Language enum & helpers with RTL support
```

### Key Components:

1. **`Strings` interface** - Defines all UI text as properties (220+ strings)
2. **Language implementations** - Each language implements `Strings`
3. **`AppLanguage` enum** - Lists available languages with RTL support
4. **`LocalStrings`** - CompositionLocal for accessing strings in Composables
5. **RTL Support** - Automatic layout direction switching for RTL languages
6. **Settings integration** - Language preference stored in `AppearanceSettings`

## 🌍 Currently Supported Languages

- 🇬🇧 **English** (`en`) - Default, LTR
- 🇷🇺 **Russian** (`ru`) - LTR
- 🇸🇦 **Arabic** (`ar`) - RTL (Right-to-Left)

## 📝 How to Use in UI Code

### In Composables:

```kotlin
@Composable
fun MyScreen() {
    val strings = LocalStrings.current

    Text(text = strings.settings)
    Button(onClick = { }) {
        Text(strings.save)
    }
}
```

### Example - Before & After:

**Before (hardcoded):**
```kotlin
Text("Settings")
Button(onClick = { }) { Text("Save") }
```

**After (localized):**
```kotlin
val strings = LocalStrings.current
Text(strings.settings)
Button(onClick = { }) { Text(strings.save) }
```

## ➕ How to Add a New Language

### Step 1: Create Language Implementation

Create a new file: `app/src/main/java/com/hyperwhisper/ime/localization/GermanStrings.kt`

```kotlin
package com.hyperwhisper.localization

object GermanStrings : Strings {
    override val appName = "HyperWhisper"
    override val imeName = "HyperWhisper Sprachtastatur"
    override val cancel = "Abbrechen"
    override val save = "Speichern"
    override val delete = "Löschen"
    // ... translate all 100+ strings
}
```

### Step 2: Add to AppLanguage Enum

Edit `AppLanguage.kt`:

```kotlin
enum class AppLanguage(
    val displayName: String,
    val nativeName: String,
    val code: String,
    val isRTL: Boolean = false  // NEW: RTL support
) {
    ENGLISH("English", "English", "en", isRTL = false),
    RUSSIAN("Russian", "Русский", "ru", isRTL = false),
    ARABIC("Arabic", "العربية", "ar", isRTL = true),
    GERMAN("German", "Deutsch", "de", isRTL = false),  // ADD THIS
}
```

### Step 3: Map in getStrings() Function

Edit `AppLanguage.kt`:

```kotlin
fun AppLanguage.getStrings(): Strings {
    return when (this) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.RUSSIAN -> RussianStrings
        AppLanguage.GERMAN -> GermanStrings  // ADD THIS
    }
}
```

**That's it!** The language will automatically appear in settings.

## 🔧 Implementation Details

### How Language Switching Works:

1. User selects language in Settings
2. Saved to `AppearanceSettings.uiLanguage` (language code: "en", "ru", etc.)
3. Persisted via DataStore
4. On app launch, Theme.kt reads language preference
5. Provides correct `Strings` implementation via `LocalStrings`
6. All Composables use `LocalStrings.current` to get localized text

### Theme Integration:

```kotlin
// In Theme.kt
@Composable
fun HyperWhisperTheme(
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val strings = getLanguageByCode(appearanceSettings.uiLanguage).getStrings()

    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme(...) {
            content()
        }
    }
}
```

## 📊 String Categories

The `Strings` interface organizes text into categories:

- **App Info** - App name, IME name
- **Common Actions** - Save, Cancel, Delete, etc.
- **Keyboard UI** - Recording, Processing, etc.
- **Settings** - API configuration, voice modes
- **Appearance** - Themes, colors, fonts
- **About Screen** - Version, description, features
- **Logs Screen** - Diagnostic logs text
- **Errors** - All error messages
- **Time Units** - Minutes, seconds

## 🎯 Migration Guide

To migrate existing hardcoded strings:

1. **Find hardcoded text:**
   ```kotlin
   Text("Settings")  // ❌ Hardcoded
   ```

2. **Replace with localized string:**
   ```kotlin
   val strings = LocalStrings.current
   Text(strings.settings)  // ✅ Localized
   ```

3. **If string doesn't exist**, add it to `Strings` interface and all implementations

## 🧪 Testing

To test multi-language:

1. Build app: `./build.sh`
2. Install on device
3. Open Settings → Appearance → Interface Language
4. Select Russian (Русский)
5. UI instantly switches to Russian
6. App restart preserves language choice

## 📱 User Experience

- **No app restart needed** - Language switches instantly
- **Persistent choice** - Saved across app restarts
- **System integration** - Can follow system language (future enhancement)
- **Fallback** - If language code invalid, defaults to English

## 🚀 Future Enhancements

## 🔄 RTL (Right-to-Left) Support

HyperWhisper fully supports RTL languages like Arabic, Hebrew, and Farsi.

### How RTL Works:

1. **Language Property**: Each language in `AppLanguage` enum has an `isRTL` property
2. **Automatic Layout**: Theme.kt automatically sets layout direction based on selected language
3. **Compose Integration**: Uses `LocalLayoutDirection` to mirror all layouts
4. **Bidirectional Text**: Text components automatically align based on direction

### RTL-enabled Languages:

- 🇸🇦 Arabic (`ar`) - Fully supported with 220+ translated strings
- 🇮🇱 Hebrew (`he`) - Ready to add
- 🇮🇷 Farsi (`fa`) - Ready to add

### Testing RTL:

1. Go to Settings → Appearance → Interface Language
2. Select Arabic (العربية)
3. UI will automatically mirror and display right-to-left
4. All text aligns to the right
5. Navigation flows from right to left

Possible improvements:

1. **Auto-detect system language** - Match Android system language on first launch
2. **Plural forms** - Handle "1 item" vs "5 items"
3. **String formatting** - Dynamic values like "Recorded %d seconds"
4. **Context-specific strings** - Same word, different contexts
5. **Translation validation** - Ensure all strings translated

## 📦 File Structure

```
com.hyperwhisper.ime.localization/
│
├── Strings.kt                  # Interface (220+ properties)
├── EnglishStrings.kt           # ~210 lines
├── RussianStrings.kt           # ~210 lines
├── ArabicStrings.kt            # ~250 lines (RTL)
├── GermanStrings.kt            # ~210 lines (example)
└── AppLanguage.kt              # ~60 lines (with RTL support)
```

## 💡 Best Practices

1. **Always use `LocalStrings.current`** - Never hardcode UI text
2. **Keep English as fallback** - Default language should be complete
3. **Test with long translations** - German/Russian words can be 2x longer
4. **Avoid string concatenation** - Provide complete sentences
5. **Context matters** - "Save" button vs "Save settings" might be different

## 🌐 Language Codes (ISO 639-1)

Common codes for future additions:
- `en` - English
- `ru` - Russian
- `de` - German
- `es` - Spanish
- `fr` - French
- `zh` - Chinese
- `ja` - Japanese
- `ar` - Arabic
- `hi` - Hindi
- `pt` - Portuguese

## 📞 Support

For questions or issues with localization:
- Check this documentation
- Review existing language implementations
- Test with both English and Russian to see patterns
- Use autocomplete to find the right string property

## ✅ Checklist for New Language

- [ ] Create `[Language]Strings.kt` implementing `Strings`
- [ ] Translate all 220+ properties
- [ ] Add enum entry to `AppLanguage` with `isRTL` flag
- [ ] Add mapping in `getStrings()`
- [ ] Test UI thoroughly with new language
- [ ] Verify long words don't break layout
- [ ] Check special characters render correctly
- [ ] For RTL languages: Verify layout mirrors correctly
- [ ] Test input field info display (added in v1.1)
