package com.hyperwhisper.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.KeyboardLayout

/**
 * Full list of keyboard localities, opened by long-pressing the locality key.
 *
 * Doubles as the enable manager — there is no separate settings screen. Each
 * row shows the language; tapping it makes that locality active (and the
 * caller adds it to the enabled set so it joins the tap-cycle). The trailing
 * checkbox toggles membership in the cycle without changing the active
 * locality, so users curate the rotation right here.
 *
 * Rendered as an in-IME [Surface] overlay rather than a Dialog — IMEs can't
 * host real Android Dialogs (BadTokenException, null window token).
 */
@Composable
fun LocalitySelectorSheet(
    currentLayout: KeyboardLayout,
    enabledLayouts: Set<KeyboardLayout>,
    onLocalitySelected: (KeyboardLayout) -> Unit,
    onToggleEnabled: (KeyboardLayout) -> Unit,
    onDismiss: () -> Unit
) {
    // No BackHandler — IMEs don't provide an OnBackPressedDispatcherOwner, so
    // calling it crashes (IllegalStateException). Dismiss via the Close button
    // or by picking a row. Same convention as LlmModelSelectorDialog.
    Surface(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Keyboard languages",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
            Text(
                text = "Tap to switch • checkbox keeps it in the tap-cycle",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardLayout.values().forEach { layout ->
                    LocalityRow(
                        layout = layout,
                        isActive = layout == currentLayout,
                        isEnabled = layout in enabledLayouts,
                        onSelect = {
                            onLocalitySelected(layout)
                            onDismiss()
                        },
                        onToggleEnabled = { onToggleEnabled(layout) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalityRow(
    layout: KeyboardLayout,
    isActive: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonChecked,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = layout.nativeName,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${layout.displayName} · ${layout.code}",
                        fontSize = 11.sp,
                        color = (if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onToggleEnabled) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckBox
                        else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (isEnabled) "In cycle" else "Add to cycle",
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
