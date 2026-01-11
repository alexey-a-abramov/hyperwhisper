package com.hyperwhisper.ui.indicators

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Recording timer display
 * Shows elapsed time during recording with warning state in final 30 seconds
 * Click to toggle between timer display and icon
 */
@Composable
fun RecordingTimer(
    durationMs: Long,
    maxDurationMs: Long,
    isVisible: Boolean,
    onToggle: () -> Unit
) {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 1000) / 60
    val isWarning = (maxDurationMs - durationMs) <= 30000 // Last 30 seconds

    Surface(
        onClick = onToggle,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isWarning) Color(0xFFE53935).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isVisible) {
                Text(
                    text = "$minutes:${seconds.toString().padStart(2, '0')}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarning) Color.Red else MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Show Timer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
