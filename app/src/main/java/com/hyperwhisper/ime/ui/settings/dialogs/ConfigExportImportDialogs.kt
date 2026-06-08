package com.hyperwhisper.ui.settings.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.config.ApplyResult
import com.hyperwhisper.data.config.PendingConfigPatch
import com.hyperwhisper.localization.LocalStrings
import com.hyperwhisper.ui.components.ConfigDiffList
import kotlinx.coroutines.launch

/**
 * Export dialog: shows the redacted JSONC configuration document with
 * copy-to-clipboard and share actions. Content never contains API keys
 * (see ConfigSnapshotProvider), so the plain clipboard is fine.
 */
@Composable
fun ConfigExportDialog(
    jsonc: String,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.advancedExportConfigTitle) },
        text = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = jsonc,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier
                        .padding(8.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("hyperwhisper-config", jsonc))
                Toast.makeText(context, strings.configCopiedToast, Toast.LENGTH_SHORT).show()
            }) { Text(strings.copy) }
        },
        dismissButton = {
            TextButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, jsonc)
                }
                context.startActivity(
                    Intent.createChooser(sendIntent, strings.advancedExportConfigTitle)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text(strings.share) }
        },
    )
}

/**
 * Import dialog: paste a JSON/JSONC config document, parse it into a diff
 * against the current settings, and confirm before anything is applied —
 * the same confirmation pipeline as voice configuration.
 */
@Composable
fun ConfigImportDialog(
    parse: suspend (String) -> PendingConfigPatch?,
    apply: suspend (PendingConfigPatch) -> ApplyResult,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var patch by remember { mutableStateOf<PendingConfigPatch?>(null) }
    var parseFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.advancedImportConfigTitle) },
        text = {
            Column {
                val pending = patch
                if (pending == null) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; parseFailed = false },
                        placeholder = { Text(strings.configImportPasteHint) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        minLines = 8,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (parseFailed) {
                        Text(
                            text = strings.configImportParseError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else if (pending.isEmpty) {
                    Text(strings.configImportNoDifferences)
                } else {
                    ConfigDiffList(
                        patch = pending,
                        invalidHeader = strings.configChangesInvalidHeader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    )
                }
            }
        },
        confirmButton = {
            val pending = patch
            if (pending == null) {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val parsed = parse(input)
                            if (parsed == null) parseFailed = true else patch = parsed
                        }
                    },
                ) { Text(strings.configImportPreview) }
            } else if (!pending.isEmpty) {
                TextButton(
                    enabled = pending.valid.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            val result = apply(pending)
                            val message = if (result.success) {
                                strings.configImportAppliedToast
                            } else {
                                result.errorMessage?.let { "${strings.configApplyFailed}: $it" }
                                    ?: strings.configApplyFailed
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    },
                ) { Text(strings.configApplyChanges) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (patch != null) patch = null else onDismiss()
            }) { Text(if (patch != null) strings.back else strings.cancel) }
        },
    )
}
