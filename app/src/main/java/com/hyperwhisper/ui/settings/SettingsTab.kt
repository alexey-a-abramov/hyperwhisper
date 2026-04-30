package com.hyperwhisper.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsTab(
    val title: String,
    val icon: ImageVector
) {
    API_CONFIG("API & Models", Icons.Default.Api),
    LOCAL_MODELS("Local Models", Icons.Default.Storage),
    LLM_CONFIG("Post-Processing", Icons.Default.AutoAwesome),
    VOICE_MODES("Voice Modes", Icons.Default.Mic),
    APPEARANCE("Appearance", Icons.Default.Palette)
}
