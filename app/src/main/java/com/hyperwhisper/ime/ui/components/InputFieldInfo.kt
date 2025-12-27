package com.hyperwhisper.ui.components

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.localization.Strings

/**
 * Displays information about the current input field
 * Shows input type, IME action, app name, and hint text
 */
@Composable
fun InputFieldInfo(
    editorInfo: EditorInfo?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Don't show anything if no editor info available
    if (editorInfo == null) return

    OutlinedCard(
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.5.dp)
        ) {
            // Line 1: Input type
            Text(
                text = "${strings.inputFieldType}: ${getInputTypeLabel(editorInfo.inputType, strings)}",
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Line 2: IME Action
            Text(
                text = "${strings.inputFieldAction}: ${getImeActionLabel(editorInfo.imeOptions, strings)}",
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Line 3: Package name (if available)
            editorInfo.packageName?.let { pkg ->
                Text(
                    text = "${strings.inputFieldApp}: ${pkg.substringAfterLast('.')}",
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Line 4: Hint text (if available)
            editorInfo.hintText?.let { hint ->
                if (hint.isNotEmpty()) {
                    Text(
                        text = hint.toString(),
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Get human-readable label for input type
 */
fun getInputTypeLabel(inputType: Int, strings: Strings): String {
    val typeClass = inputType and InputType.TYPE_MASK_CLASS
    val typeVariation = inputType and InputType.TYPE_MASK_VARIATION

    return when (typeClass) {
        InputType.TYPE_CLASS_TEXT -> {
            when (typeVariation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> strings.fieldTypeEmail

                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> strings.fieldTypePassword

                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> strings.fieldTypeUrl

                else -> if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
                    strings.fieldTypeMultiline
                } else {
                    strings.fieldTypeText
                }
            }
        }
        InputType.TYPE_CLASS_NUMBER -> strings.fieldTypeNumber
        InputType.TYPE_CLASS_PHONE -> strings.fieldTypePhone
        InputType.TYPE_CLASS_DATETIME -> "Date/Time"
        else -> strings.fieldTypeUnknown
    }
}

/**
 * Get human-readable label for IME action
 */
fun getImeActionLabel(imeOptions: Int, strings: Strings): String {
    return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_DONE -> strings.actionDone
        EditorInfo.IME_ACTION_GO -> strings.actionGo
        EditorInfo.IME_ACTION_SEARCH -> strings.actionSearch
        EditorInfo.IME_ACTION_SEND -> strings.actionSend
        EditorInfo.IME_ACTION_NEXT -> strings.actionNext
        EditorInfo.IME_ACTION_PREVIOUS -> strings.actionPrevious
        else -> strings.actionNone
    }
}
