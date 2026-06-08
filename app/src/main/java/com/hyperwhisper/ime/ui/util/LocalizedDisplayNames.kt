package com.hyperwhisper.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ColorSchemeOption
import com.hyperwhisper.data.DarkModePreference
import com.hyperwhisper.data.FontFamilyOption
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.KeyboardLayout
import com.hyperwhisper.data.LlmProvider
import com.hyperwhisper.data.ProcessingStage
import com.hyperwhisper.data.UIScaleOption
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.EmojiData

/**
 * Composable extension functions that resolve the localized display name for
 * the various enums that previously hard-coded English in `displayName`
 * constructor fields.
 *
 * The original `val displayName: String` properties are kept on each enum so
 * non-Composable callers (logs, JSON serialization, persistence) can keep
 * using them. Composable call sites should switch to these helpers so labels
 * follow the user's chosen interface language.
 */

// Enum: DarkModePreference
@Composable
@ReadOnlyComposable
fun DarkModePreference.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        DarkModePreference.SYSTEM -> s.darkModeFollowSystem
        DarkModePreference.LIGHT -> s.darkModeAlwaysLight
        DarkModePreference.DARK -> s.darkModeAlwaysDark
    }
}

// Enum: LlmProvider
@Composable
@ReadOnlyComposable
fun LlmProvider.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        LlmProvider.NONE -> s.providerLlmNone
        LlmProvider.OPENAI -> s.providerLlmOpenai
        LlmProvider.DEEPSEEK -> s.providerLlmDeepseek
        LlmProvider.GEMINI -> s.providerLlmGemini
        LlmProvider.ANTHROPIC -> s.providerLlmAnthropic
        LlmProvider.MISTRAL -> s.providerLlmMistral
        LlmProvider.GROQ -> s.providerLlmGroq
        LlmProvider.OPENROUTER -> s.providerLlmOpenrouter
        LlmProvider.OPENAI_COMPATIBLE -> s.providerLlmOpenaiCompatible
        LlmProvider.LOCAL_GEMMA -> s.providerLlmLocalGemma
        LlmProvider.LOCAL_LLAMACPP -> s.providerLlmLocalLlamacpp
    }
}

// Enum: ApiProvider
@Composable
@ReadOnlyComposable
fun ApiProvider.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        ApiProvider.OPENAI -> s.providerApiOpenai
        ApiProvider.DEEPGRAM -> s.providerApiDeepgram
        ApiProvider.ASSEMBLYAI -> s.providerApiAssemblyai
        ApiProvider.GOOGLE_CLOUD -> s.providerApiGoogleCloud
        ApiProvider.AWS_TRANSCRIBE -> s.providerApiAwsTranscribe
        ApiProvider.AZURE_SPEECH -> s.providerApiAzureSpeech
        ApiProvider.DEEPSEEK -> s.providerApiDeepseek
        ApiProvider.MISTRAL -> s.providerApiMistral
        ApiProvider.REVAI -> s.providerApiRevai
        ApiProvider.GROQ -> s.providerApiGroq
        ApiProvider.OPENROUTER -> s.providerApiOpenrouter
        ApiProvider.GEMINI -> s.providerApiGemini
        ApiProvider.ANTIGRAVITY -> s.providerApiAntigravity
        ApiProvider.HUGGINGFACE -> s.providerApiHuggingface
        ApiProvider.SELFHOSTED_WHISPER -> s.providerApiSelfhostedWhisper
        ApiProvider.LOCAL_WHISPER -> s.providerApiLocalWhisper
    }
}

// Enum: ProcessingStage
@Composable
@ReadOnlyComposable
fun ProcessingStage.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        ProcessingStage.PREPARING -> s.processingStagePreparing
        ProcessingStage.CONVERTING_AUDIO -> s.processingStageConvertingAudio
        ProcessingStage.LOADING_MODEL -> s.processingStageLoadingModel
        ProcessingStage.TRANSCRIBING -> s.processingStageTranscribing
        ProcessingStage.POST_PROCESSING -> s.processingStagePostProcessing
        ProcessingStage.UPLOADING -> s.processingStageUploading
        ProcessingStage.WAITING_API -> s.processingStageWaitingApi
        ProcessingStage.FINISHING -> s.processingStageFinishing
    }
}

// Enum: ColorSchemeOption
@Composable
@ReadOnlyComposable
fun ColorSchemeOption.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        ColorSchemeOption.TERMINAL_DARK -> s.themeTerminalDark
        ColorSchemeOption.OCEAN_DEEP -> s.themeOceanDeep
        ColorSchemeOption.FOREST_NIGHT -> s.themeForestNight
        ColorSchemeOption.SUNSET_HORIZON -> s.themeSunsetHorizon
        ColorSchemeOption.ARCTIC_FROST -> s.themeArcticFrost
        ColorSchemeOption.DESERT_STORM -> s.themeDesertStorm
        ColorSchemeOption.NEON_CITY -> s.themeNeonCity
        ColorSchemeOption.CHERRY_BLOSSOM -> s.themeCherryBlossom
        ColorSchemeOption.MIDNIGHT_SKY -> s.themeMidnightSky
        ColorSchemeOption.LAVA_FLOW -> s.themeLavaFlow
        ColorSchemeOption.MISTY_MOUNTAIN -> s.themeMistyMountain
        ColorSchemeOption.AUTUMN_LEAVES -> s.themeAutumnLeaves
        ColorSchemeOption.PROFESSIONAL_BLUE -> s.themeProfessionalBlue
        ColorSchemeOption.WARM_EARTH -> s.themeWarmEarth
        ColorSchemeOption.COOL_SLATE -> s.themeCoolSlate
        ColorSchemeOption.VIBRANT_PURPLE -> s.themeVibrantPurple
        ColorSchemeOption.EMERALD_GREEN -> s.themeEmeraldGreen
        ColorSchemeOption.RUBY_RED -> s.themeRubyRed
    }
}

// Enum: UIScaleOption
@Composable
@ReadOnlyComposable
fun UIScaleOption.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        UIScaleOption.VERY_SMALL -> s.uiScaleVerySmall
        UIScaleOption.SMALL -> s.uiScaleSmall
        UIScaleOption.MEDIUM -> s.uiScaleMedium
        UIScaleOption.LARGE -> s.uiScaleLarge
        UIScaleOption.VERY_LARGE -> s.uiScaleVeryLarge
    }
}

// Enum: FontFamilyOption
@Composable
@ReadOnlyComposable
fun FontFamilyOption.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        FontFamilyOption.DEFAULT -> s.fontFamilyDefault
        FontFamilyOption.SERIF -> s.fontFamilySerif
        FontFamilyOption.SANS_SERIF -> s.fontFamilySansSerif
        FontFamilyOption.MONOSPACE -> s.fontFamilyMonospace
        FontFamilyOption.CURSIVE -> s.fontFamilyCursive
    }
}

// Enum: KeyboardInputMode
@Composable
@ReadOnlyComposable
fun KeyboardInputMode.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        KeyboardInputMode.DICTATION -> s.keyboardModeDictation
        KeyboardInputMode.QWERTY -> s.keyboardModeQwerty
        KeyboardInputMode.CODE -> s.keyboardModeCode
        KeyboardInputMode.EMOJI -> s.keyboardModeEmoji
        KeyboardInputMode.AGENT_CLAUDE_CODE -> s.keyboardModeAgentClaudeCode
        KeyboardInputMode.AGENT_OPENCODE -> s.keyboardModeAgentOpencode
        KeyboardInputMode.AGENT_GEMINI -> s.keyboardModeAgentGemini
        KeyboardInputMode.AGENT_CODEX -> s.keyboardModeAgentCodex
        KeyboardInputMode.AGENT_MACROS -> s.keyboardModeAgentMacros
        KeyboardInputMode.EXPERIMENTAL_TERMINAL -> s.keyboardModeExperimentalTerminal
        KeyboardInputMode.SPECIAL_CHARS -> s.keyboardModeSpecialChars
        KeyboardInputMode.SYSTEM_KEYS -> s.keyboardModeSystemKeys
        KeyboardInputMode.VIBE_CODING -> s.keyboardModeVibeCoding
        KeyboardInputMode.NUMPAD -> s.keyboardModeNumpad
    }
}

// Enum: KeyboardLayout
@Composable
@ReadOnlyComposable
fun KeyboardLayout.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        KeyboardLayout.ENGLISH -> s.keyboardLayoutEnglish
        KeyboardLayout.RUSSIAN -> s.keyboardLayoutRussian
        KeyboardLayout.SPANISH -> s.keyboardLayoutSpanish
        KeyboardLayout.FRENCH -> s.keyboardLayoutFrench
        KeyboardLayout.GERMAN -> s.keyboardLayoutGerman
        KeyboardLayout.ARABIC -> s.keyboardLayoutArabic
        // Localities added with the international-keyboard rollout fall back to
        // their native name (no per-string translation entry yet).
        else -> nativeName
    }
}

// Enum: EmojiData.EmojiCategory
@Composable
@ReadOnlyComposable
fun EmojiData.EmojiCategory.localizedDisplayName(): String {
    val s = LocalStrings.current
    return when (this) {
        EmojiData.EmojiCategory.SMILEYS -> s.emojiCategorySmileys
        EmojiData.EmojiCategory.PEOPLE -> s.emojiCategoryPeople
        EmojiData.EmojiCategory.ANIMALS -> s.emojiCategoryAnimals
        EmojiData.EmojiCategory.FOOD -> s.emojiCategoryFood
        EmojiData.EmojiCategory.ACTIVITIES -> s.emojiCategoryActivities
        EmojiData.EmojiCategory.TRAVEL -> s.emojiCategoryTravel
        EmojiData.EmojiCategory.OBJECTS -> s.emojiCategoryObjects
        EmojiData.EmojiCategory.SYMBOLS -> s.emojiCategorySymbols
        EmojiData.EmojiCategory.FLAGS -> s.emojiCategoryFlags
    }
}
