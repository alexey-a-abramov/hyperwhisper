package com.hyperwhisper.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.config.ChangeError
import com.hyperwhisper.data.config.PendingConfigPatch
import com.hyperwhisper.data.config.ResolvedChange

/**
 * Renders the rows of a pending configuration patch: one "Label: old → new"
 * row per valid change, followed by error rows for changes that failed
 * validation. Shared between the in-keyboard confirmation overlay and the
 * settings import dialog so both confirmation surfaces look identical.
 */
@Composable
fun ConfigDiffList(
    patch: PendingConfigPatch,
    invalidHeader: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(patch.valid) { change ->
            ConfigChangeRow(change)
        }
        if (patch.errors.isNotEmpty()) {
            item {
                Text(
                    text = invalidHeader,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(patch.errors) { error ->
                ConfigErrorRow(error)
            }
        }
    }
}

@Composable
private fun ConfigChangeRow(change: ResolvedChange) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = change.field.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = change.oldDisplay,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = "  →  ",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = change.newDisplay,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ConfigErrorRow(error: ChangeError) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${error.path}: ${error.rawValue}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = error.reason,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
        )
    }
}
