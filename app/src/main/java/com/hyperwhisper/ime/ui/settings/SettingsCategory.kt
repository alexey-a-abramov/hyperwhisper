package com.hyperwhisper.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import com.hyperwhisper.localization.LocalStrings

/**
 * The constructor `title` and `subtitle` fields below stay populated with
 * English so any non-Composable consumer keeps working. UI surfaces that have
 * a [LocalStrings] in scope should call [localizedTitle] / [localizedSubtitle]
 * instead so the labels follow the user's chosen interface language.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    TRANSCRIPTION(
        title = "Transcription",
        subtitle = "Provider, model, languages",
        icon = Icons.Outlined.GraphicEq
    ),
    POST_PROCESSING(
        title = "Post-processing",
        subtitle = "LLM for transforms & translation",
        icon = Icons.Outlined.AutoAwesome
    ),
    LOCAL_MODELS(
        title = "Local models",
        subtitle = "Download Whisper + Gemma models on-device",
        icon = Icons.Outlined.CloudDownload
    ),
    VOICE_MODES(
        title = "Voice Modes",
        subtitle = "Custom prompts for transcriptions",
        icon = Icons.Outlined.Mic
    ),
    KEYBOARD_BEHAVIOR(
        title = "Keyboard & Behavior",
        subtitle = "History, clipboard, audio files",
        icon = Icons.Outlined.Keyboard
    ),
    APPEARANCE(
        title = "Appearance",
        subtitle = "Theme, fonts, interface language",
        icon = Icons.Outlined.Palette
    ),
    ADVANCED(
        title = "Advanced",
        subtitle = "Logs, integrations, diagnostics",
        icon = Icons.Outlined.Tune
    ),
    ABOUT(
        title = "About",
        subtitle = "Credits, license, version",
        icon = Icons.Outlined.Info
    );
}

sealed class SettingsRoute {
    object Home : SettingsRoute()
    data class Detail(val category: SettingsCategory) : SettingsRoute()
}

@Composable
@ReadOnlyComposable
fun SettingsCategory.localizedTitle(): String {
    val s = LocalStrings.current
    return when (this) {
        SettingsCategory.TRANSCRIPTION -> s.categoryTranscriptionTitle
        SettingsCategory.POST_PROCESSING -> s.categoryPostProcessingTitle
        SettingsCategory.LOCAL_MODELS -> s.categoryLocalModelsTitle
        SettingsCategory.VOICE_MODES -> s.categoryVoiceModesTitle
        SettingsCategory.KEYBOARD_BEHAVIOR -> s.categoryKeyboardBehaviorTitle
        SettingsCategory.APPEARANCE -> s.categoryAppearanceTitle
        SettingsCategory.ADVANCED -> s.categoryAdvancedTitle
        SettingsCategory.ABOUT -> s.categoryAboutTitle
    }
}

@Composable
@ReadOnlyComposable
fun SettingsCategory.localizedSubtitle(): String {
    val s = LocalStrings.current
    return when (this) {
        SettingsCategory.TRANSCRIPTION -> s.categoryTranscriptionSubtitle
        SettingsCategory.POST_PROCESSING -> s.categoryPostProcessingSubtitle
        SettingsCategory.LOCAL_MODELS -> s.categoryLocalModelsSubtitle
        SettingsCategory.VOICE_MODES -> s.categoryVoiceModesSubtitle
        SettingsCategory.KEYBOARD_BEHAVIOR -> s.categoryKeyboardBehaviorSubtitle
        SettingsCategory.APPEARANCE -> s.categoryAppearanceSubtitle
        SettingsCategory.ADVANCED -> s.categoryAdvancedSubtitle
        SettingsCategory.ABOUT -> s.categoryAboutSubtitle
    }
}
