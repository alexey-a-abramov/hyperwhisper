package com.hyperwhisper.ui.sections

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyperwhisper.data.RecordingState
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.components.HamburgerMenu
import com.hyperwhisper.ui.selectors.ModeSelector

/**
 * Top controls row of the keyboard
 * Layout: Hamburger Menu | Keyboard Switcher | Mode Selector (expanded)
 */
@Composable
fun TopControlsRow(
    context: Context,
    voiceModes: List<VoiceMode>,
    selectedModeId: String,
    recordingState: RecordingState,
    showKeyboardSwitcher: Boolean,
    techieModeEnabled: Boolean,
    onSwitchKeyboard: () -> Unit,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger Menu (Settings, Logs, About)
        HamburgerMenu(
            context = context,
            techieModeEnabled = techieModeEnabled
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Keyboard Switcher button (only shown if enabled in settings)
        if (showKeyboardSwitcher) {
            IconButton(onClick = onSwitchKeyboard) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = strings.switchKeyboardDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        // Mode Selector - takes remaining space
        ModeSelector(
            modes = voiceModes,
            selectedModeId = selectedModeId,
            onModeSelected = onModeSelected,
            enabled = recordingState == RecordingState.IDLE,
            modifier = Modifier.weight(1f)
        )
    }
}
