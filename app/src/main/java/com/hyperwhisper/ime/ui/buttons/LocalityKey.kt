package com.hyperwhisper.ui.buttons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.ui.KeyboardMetrics
import com.hyperwhisper.ui.components.LongPressIndicator

/**
 * Locality (keyboard-language) switcher key. Shows the active locality's
 * two-letter code, tinted with the primary container so it reads as a control
 * rather than a character. Tap cycles the enabled localities (and points
 * dictation at that language); long-press (orange dot) opens the full list.
 *
 * Shared by the dictation bottom row and the QWERTY typing row so the same
 * control — and muscle memory — is available whether the user is speaking or
 * typing. The caller sizes it via [modifier].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalityKey(
    code: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(KeyboardMetrics.KeyRadius),
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = code,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LongPressIndicator(padding = 3.dp)
    }
}
