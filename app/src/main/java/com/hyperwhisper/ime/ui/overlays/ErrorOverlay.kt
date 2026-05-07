package com.hyperwhisper.ui.overlays

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.ErrorKind
import com.hyperwhisper.data.classifyErrorMessage
import com.hyperwhisper.localization.LocalStrings

/**
 * Full-screen error overlay shown over the IME.
 *
 * Renders a plain-language summary of what went wrong (derived from the
 * raw [errorMessage]) and one suggested manual action — never auto-retries
 * or fallback-routes (per roadmap §D, the user explicitly rejected
 * automatic recovery). The raw message is still shown verbatim so the user
 * can copy it for support / debugging.
 */
@Composable
fun ErrorOverlay(
    errorMessage: String,
    onDismiss: () -> Unit,
    context: Context,
    providerName: String? = null,
    onSwitchProvider: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val isPermissionError = errorMessage.contains("permission", ignoreCase = true)
    val kind = classifyErrorMessage(errorMessage)
    val summary = plainLanguageSummary(kind, providerName, strings)

    // Full-screen overlay within keyboard (not a separate Dialog window)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 16.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = strings.error,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                // Plain-language summary line — same color as title but
                // unbolded so it reads as an explanation.
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f),
                    lineHeight = 17.sp
                )

                // Raw error message (scrollable). Kept verbatim because the
                // user often needs to copy it for support / search.
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Top
                ) {
                    item {
                        Text(
                            text = errorMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Suggested-action button. One per kind, full width above
                // the copy/dismiss row. Permission errors (orthogonal to
                // the API kinds) keep their existing Settings shortcut.
                if (isPermissionError) {
                    SuggestedActionButton(
                        label = strings.openSettings,
                        icon = Icons.Default.Settings,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                } else {
                    SuggestedActionForKind(
                        kind = kind,
                        context = context,
                        onSwitchProvider = onSwitchProvider,
                    )
                }

                // Action buttons row (Copy + Dismiss)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Error Message", errorMessage)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            strings.copyError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Dismiss button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Text(
                            strings.dismiss,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedActionForKind(
    kind: ErrorKind,
    context: Context,
    onSwitchProvider: (() -> Unit)?,
) {
    val strings = LocalStrings.current
    when (kind) {
        ErrorKind.AUTH -> SuggestedActionButton(
            label = strings.openSettings,
            icon = Icons.Default.Settings,
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching { context.startActivity(intent) }
            }
        )
        ErrorKind.MODEL_NOT_FOUND,
        ErrorKind.PROVIDER_DOWN,
        ErrorKind.RATE_LIMITED -> {
            // "Switch provider" only when the caller wired up the picker.
            // Otherwise no button — the summary text already tells the user
            // what to do, and we don't want a dead-end button.
            if (onSwitchProvider != null) {
                SuggestedActionButton(
                    label = strings.errorOverlaySwitchProvider,
                    icon = Icons.Default.SwapHoriz,
                    onClick = onSwitchProvider
                )
            }
        }
        ErrorKind.NETWORK,
        ErrorKind.TIMEOUT,
        ErrorKind.UNKNOWN -> {
            // No action — these are transient or unclassifiable.
            // The summary line already says "try again later".
        }
    }
}

@Composable
private fun SuggestedActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onErrorContainer,
            contentColor = MaterialTheme.colorScheme.errorContainer
        ),
        contentPadding = PaddingValues(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Short user-facing explanation per error kind. Strings come from the
 *  shared [com.hyperwhisper.localization.Strings] table so they translate
 *  alongside the rest of the UI. The provider name is interpolated as %1$s;
 *  callers pass null when no provider context is available, in which case
 *  the locale's [Strings.errorOverlayProviderFallback] is used. */
private fun plainLanguageSummary(
    kind: ErrorKind,
    providerName: String?,
    strings: com.hyperwhisper.localization.Strings,
): String {
    val who = providerName?.takeIf { it.isNotBlank() } ?: strings.errorOverlayProviderFallback
    val template = when (kind) {
        ErrorKind.AUTH -> strings.errorOverlaySummaryAuthFormat
        ErrorKind.MODEL_NOT_FOUND -> strings.errorOverlaySummaryModelNotFoundFormat
        ErrorKind.RATE_LIMITED -> strings.errorOverlaySummaryRateLimitedFormat
        ErrorKind.TIMEOUT -> strings.errorOverlaySummaryTimeoutFormat
        ErrorKind.PROVIDER_DOWN -> strings.errorOverlaySummaryProviderDownFormat
        ErrorKind.NETWORK -> strings.errorOverlaySummaryNetworkFormat
        ErrorKind.UNKNOWN -> strings.errorOverlaySummaryUnknownFormat
    }
    return String.format(template, who)
}
