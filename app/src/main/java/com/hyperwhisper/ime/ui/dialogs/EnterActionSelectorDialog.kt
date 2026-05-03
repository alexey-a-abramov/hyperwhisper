package com.hyperwhisper.ui.dialogs

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler

enum class EnterAction {
    NEWLINE,
    SUBMIT,
    LINE_BREAK
}

@Composable
fun EnterActionSelectorDialog(
    editorInfo: EditorInfo?,
    onActionSelected: (EnterAction) -> Unit,
    onDismiss: () -> Unit
) {
    // Determine the best default action based on editor info
    val isMultiLine = editorInfo?.inputType?.and(android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    val imeAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)

    val recommendedAction = when {
        isMultiLine && (imeAction == EditorInfo.IME_ACTION_NONE || imeAction == EditorInfo.IME_ACTION_UNSPECIFIED) -> EnterAction.NEWLINE
        imeAction != null && imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED -> EnterAction.SUBMIT
        else -> EnterAction.NEWLINE
    }

    // IMEs cannot host real Android Dialogs (BadTokenException — token null
    // is not valid). Render as a full-screen overlay inside the IME composition.
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter Key Action",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Choose what the Enter key should do:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Newline option
                ActionOption(
                    icon = Icons.Default.KeyboardReturn,
                    title = "Newline",
                    description = "Insert a line break (↵)",
                    isRecommended = recommendedAction == EnterAction.NEWLINE,
                    onClick = { onActionSelected(EnterAction.NEWLINE) }
                )

                // Submit option
                ActionOption(
                    icon = Icons.Default.Send,
                    title = "Submit",
                    description = "Send form or perform action",
                    isRecommended = recommendedAction == EnterAction.SUBMIT,
                    onClick = { onActionSelected(EnterAction.SUBMIT) }
                )

                // Line break option (same as newline but explicit)
                ActionOption(
                    icon = Icons.Default.Notes,
                    title = "Line Break",
                    description = "Add blank line (double ↵)",
                    isRecommended = false,
                    onClick = { onActionSelected(EnterAction.LINE_BREAK) }
                )
            }
        }
    }

@Composable
private fun ActionOption(
    icon: ImageVector,
    title: String,
    description: String,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isRecommended) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isRecommended) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRecommended) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Recommended",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = if (isRecommended) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
