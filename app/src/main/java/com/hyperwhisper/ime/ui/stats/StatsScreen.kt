package com.hyperwhisper.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperwhisper.data.telemetry.ColdStartKind
import com.hyperwhisper.data.telemetry.SessionWithPhases

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
    onExportClick: () -> Unit
) {
    val window by viewModel.window.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val predictionEnabled by viewModel.predictionEnabled.collectAsState()
    val predictionStatus by viewModel.predictionStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Latency Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export JSONL",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WindowSelector(
                    selected = window,
                    onSelect = viewModel::setWindow,
                    totalCount = totalCount
                )
            }

            item {
                PredictionCard(
                    enabled = predictionEnabled,
                    status = predictionStatus,
                    onToggle = viewModel::setPredictionEnabled,
                    onRecalculate = viewModel::recalculate
                )
            }

            if (summaries.isEmpty()) {
                item {
                    EmptyState(totalCount = totalCount, window = window)
                }
            } else {
                item {
                    SectionHeader("Per-model summary")
                }
                items(summaries) { ms ->
                    ModelSummaryCard(ms)
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionHeader("Recent sessions") }
                items(recent) { sp ->
                    RecentSessionItem(sp)
                }
            }

            item {
                Text(
                    text = "Tip: tap the download icon to export JSONL for notebook analysis.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowSelector(
    selected: TimeWindow,
    onSelect: (TimeWindow) -> Unit,
    totalCount: Int
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeWindow.values().forEach { tw ->
                FilterChip(
                    selected = tw == selected,
                    onClick = { onSelect(tw) },
                    label = { Text(tw.label) }
                )
            }
        }
        Text(
            text = "$totalCount total sessions in DB",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun PredictionCard(
    enabled: Boolean,
    status: PredictionUiState,
    onToggle: (Boolean) -> Unit,
    onRecalculate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Statistics-based progress",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Predict the local progress bar from these gathered timings instead of a fixed guess.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRecalculate,
                    enabled = status != PredictionUiState.Running
                ) {
                    Text(if (status == PredictionUiState.Running) "Recalculating…" else "Recalculate")
                }
                Text(
                    text = predictionStatusText(status),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun predictionStatusText(status: PredictionUiState): String = when (status) {
    is PredictionUiState.Idle -> "Tap to calibrate from recorded sessions."
    is PredictionUiState.Running -> "Analyzing sessions…"
    is PredictionUiState.Done -> {
        val s = status.summary
        if (s.modelsCalibrated > 0)
            "Calibrated ${s.modelsCalibrated} model(s) from ${s.totalSessions} session(s)."
        else
            "Not enough data yet (${s.totalSessions} session(s)) — keep dictating, then recalculate."
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyState(totalCount: Int, window: TimeWindow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "No sessions in window: ${window.label}",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (totalCount == 0)
                    "Start transcribing to populate latency telemetry."
                else
                    "Try a wider window — $totalCount total session(s) recorded.",
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ModelSummaryCard(s: ModelSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.modelId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${s.sessionType.name.lowercase()} · ${s.count} runs (${s.successCount} ok)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            StatLine("mean", formatMs(s.meanWallMs))
            StatLine("p50",  formatMs(s.p50WallMs))
            StatLine("p95",  formatMs(s.p95WallMs))
            StatLine(
                label = "audio:wall ratio",
                value = "%.2fx".format(s.audioToWallRatio)
            )

            if (s.coldStartBreakdown.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Cold-start breakdown (p50)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColdStartKind.values().forEach { kind ->
                    s.coldStartBreakdown[kind]?.let { bucket ->
                        StatLine(
                            label = "  ${kind.name.lowercase()} (${bucket.count})",
                            value = formatMs(bucket.p50WallMs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp)
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentSessionItem(sp: SessionWithPhases) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sp.session.modelId,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (sp.session.success) "ok" else "fail"} · " +
                                "${sp.session.coldStartKind.name.lowercase()} · " +
                                formatMs(sp.session.totalWallMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "hide" else "phases") },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 6.dp))
                if (sp.phases.isEmpty()) {
                    Text(
                        text = "(no phases recorded)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sp.phases.sortedBy { it.ordinal }.forEach { ph ->
                        StatLine(label = ph.phaseName, value = formatMs(ph.durationMs))
                    }
                }
                if (sp.session.errorKind != null) {
                    Text(
                        text = "error: ${sp.session.errorKind}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String = when {
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "%.2fs".format(ms / 1000.0)
    else -> "%dm %ds".format(ms / 60_000, (ms % 60_000) / 1000)
}
