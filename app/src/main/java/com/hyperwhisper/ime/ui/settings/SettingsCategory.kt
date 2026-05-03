package com.hyperwhisper.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

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
    UPDATES(
        title = "Updates",
        subtitle = "Check for new versions",
        icon = Icons.Outlined.SystemUpdate
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
