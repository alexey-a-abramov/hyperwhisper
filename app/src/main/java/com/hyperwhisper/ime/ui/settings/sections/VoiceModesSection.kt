package com.hyperwhisper.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.VoiceMode
import com.hyperwhisper.ui.settings.components.cards.ModeCard

@Composable
fun VoiceModesSection(
    voiceModes: List<VoiceMode>,
    onAddMode: () -> Unit,
    onEditMode: (VoiceMode) -> Unit,
    onDeleteMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Header with title and add button
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Voice Modes",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onAddMode) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Mode",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Voice modes list - each mode in its own row
    voiceModes.forEach { mode ->
        ModeCard(
            mode = mode,
            onEdit = { onEditMode(mode) },
            onDelete = { onDeleteMode(mode.id) }
        )
    }
}
