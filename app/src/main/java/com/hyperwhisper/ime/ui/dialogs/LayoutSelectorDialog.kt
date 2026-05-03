package com.hyperwhisper.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.hyperwhisper.data.KeyboardLayout
import com.hyperwhisper.data.KeyboardInputMode
import com.hyperwhisper.data.VoiceMode

@Composable
fun LayoutSelectorDialog(
    currentLayout: KeyboardLayout,
    currentMode: KeyboardInputMode,
    enabledLayouts: Set<KeyboardLayout>,
    currentInputLanguage: String,
    currentOutputLanguage: String,
    currentVoiceMode: VoiceMode?,
    onLayoutSelected: (KeyboardLayout) -> Unit,
    onModeSelected: (KeyboardInputMode) -> Unit,
    onShowInputLanguageDialog: () -> Unit,
    onShowOutputLanguageDialog: () -> Unit,
    onShowVoiceModeDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    // IMEs cannot host real Android Dialogs (BadTokenException — token null).
    // Render as a full-screen Surface overlay inside the IME composition instead.
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keyboard Layout & Mode",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Keyboard Layouts Section
                SectionTitle("KEYBOARD LAYOUTS")
                Spacer(modifier = Modifier.height(8.dp))

                // Create rows of 3 layouts each
                val layoutRows = KeyboardLayout.values().toList().chunked(3)
                layoutRows.forEach { rowLayouts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowLayouts.forEach { layout ->
                            LayoutButton(
                                layout = layout,
                                isSelected = layout == currentLayout,
                                isEnabled = layout in enabledLayouts,
                                onClick = {
                                    if (layout in enabledLayouts) {
                                        onLayoutSelected(layout)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if row has fewer than 3 items
                        repeat(3 - rowLayouts.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Modes Section
                SectionTitle("INPUT MODES")
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        icon = Icons.Default.Mic,
                        label = "Dictation",
                        isSelected = currentMode == KeyboardInputMode.DICTATION,
                        onClick = {
                            onModeSelected(KeyboardInputMode.DICTATION)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        icon = Icons.Default.Keyboard,
                        label = "Text",
                        isSelected = currentMode == KeyboardInputMode.QWERTY || currentMode == KeyboardInputMode.SPECIAL_CHARS,
                        onClick = {
                            onModeSelected(KeyboardInputMode.QWERTY)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        icon = Icons.Default.Dialpad,
                        label = "Numpad",
                        isSelected = currentMode == KeyboardInputMode.NUMPAD,
                        onClick = {
                            onModeSelected(KeyboardInputMode.NUMPAD)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        icon = Icons.Default.Code,
                        label = "Vibe Coding",
                        isSelected = currentMode == KeyboardInputMode.VIBE_CODING,
                        onClick = {
                            onModeSelected(KeyboardInputMode.VIBE_CODING)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        icon = Icons.Default.EmojiEmotions,
                        label = "Emoji",
                        isSelected = currentMode == KeyboardInputMode.EMOJI,
                        onClick = {
                            onModeSelected(KeyboardInputMode.EMOJI)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Voice Settings Section
                SectionTitle("VOICE SETTINGS")
                Spacer(modifier = Modifier.height(8.dp))

                SettingButton(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "Input Language",
                    value = currentInputLanguage.ifEmpty { "Auto" },
                    onClick = {
                        onShowInputLanguageDialog()
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingButton(
                    icon = Icons.Default.Language,
                    label = "Output Language",
                    value = currentOutputLanguage.ifEmpty { "Original" },
                    onClick = {
                        onShowOutputLanguageDialog()
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingButton(
                    icon = Icons.Default.Tune,
                    label = "Voice Mode",
                    value = currentVoiceMode?.name ?: "Default",
                    onClick = {
                        onShowVoiceModeDialog()
                        onDismiss()
                    }
                )
            }
        }
    }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun LayoutButton(
    layout: KeyboardLayout,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(70.dp)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        enabled = isEnabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = layout.code,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = layout.nativeName,
                fontSize = 10.sp,
                color = contentColor.copy(alpha = 0.8f)
            )
            if (!isEnabled) {
                Text(
                    text = "(disabled)",
                    fontSize = 8.sp,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun SettingButton(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
