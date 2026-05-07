package com.hyperwhisper.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.network.ConnectionTester

/**
 * Compact status chip for a provider row in Settings detail screens. Shows
 * the most useful one-glance summary about a provider:
 *
 *  - Live [retestState] takes precedence (queued / spinner / ✓ / ✗) so the
 *    user can watch retest-all progress without leaving the section they
 *    were configuring.
 *  - Otherwise [testedAt] formats to "✓ 2h" / "stale" / nothing.
 *
 * Returns nothing rendered when both inputs are null and no test has been
 * recorded — caller doesn't need to gate the call site.
 */
@Composable
fun ProviderStatusChip(
    testedAt: Long?,
    retestState: ConnectionTester.RetestRowState? = null,
) {
    when (retestState) {
        is ConnectionTester.RetestRowState.Pending -> InfoChip("queued")
        is ConnectionTester.RetestRowState.Running -> Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
                Text("testing", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        is ConnectionTester.RetestRowState.Ok -> StatusPill(
            label = "tested",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            leadingIcon = Icons.Filled.CheckCircle,
        )
        is ConnectionTester.RetestRowState.Error -> StatusPill(
            label = "failed",
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            leadingIcon = Icons.Filled.Cancel,
        )
        null -> {
            val label = formatTestedAgo(testedAt) ?: return
            val isStale = label == "stale"
            StatusPill(
                label = label,
                container = if (isStale) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                content = if (isStale) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                leadingIcon = if (isStale) null else Icons.Filled.Check,
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.size(2.dp))
            }
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
