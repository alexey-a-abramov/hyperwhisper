package com.hyperwhisper.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.prediction.CalibrationSummary
import com.hyperwhisper.data.telemetry.ColdStartKind
import com.hyperwhisper.data.telemetry.PerformanceRepository
import com.hyperwhisper.data.telemetry.SessionLatencyRow
import com.hyperwhisper.data.telemetry.SessionType
import com.hyperwhisper.data.telemetry.SessionWithPhases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class TimeWindow(val days: Int?, val label: String) {
    D1(1, "24h"),
    D7(7, "7d"),
    D30(30, "30d"),
    ALL(null, "All")
}

data class ColdBucket(val count: Int, val p50WallMs: Long)

data class ModelSummary(
    val modelId: String,
    val sessionType: SessionType,
    val count: Int,
    val successCount: Int,
    val meanWallMs: Long,
    val p50WallMs: Long,
    val p95WallMs: Long,
    val audioToWallRatio: Double,
    val coldStartBreakdown: Map<ColdStartKind, ColdBucket>
)

/** State of the "Recalculate" progress-prediction calibration action. */
sealed interface PredictionUiState {
    object Idle : PredictionUiState
    object Running : PredictionUiState
    data class Done(val summary: CalibrationSummary) : PredictionUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val perf: PerformanceRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _window = MutableStateFlow(TimeWindow.D7)
    val window: StateFlow<TimeWindow> = _window.asStateFlow()

    /** Whether local progress is predicted from gathered statistics (default ON). */
    val predictionEnabled: StateFlow<Boolean> = settingsRepository.apiSettings
        .map { it.localModelSettings.statisticsPrediction ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _predictionStatus = MutableStateFlow<PredictionUiState>(PredictionUiState.Idle)
    val predictionStatus: StateFlow<PredictionUiState> = _predictionStatus.asStateFlow()

    val totalCount: StateFlow<Int> = perf.totalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rowsFlow = _window.flatMapLatest { tw ->
        val since = if (tw.days == null) 0L
                    else System.currentTimeMillis() - tw.days * 86_400_000L
        perf.latencyRowsSince(since)
    }

    val summaries: StateFlow<List<ModelSummary>> = rowsFlow
        .map(::computeSummaries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<SessionWithPhases>> = perf.recentSessions(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setWindow(tw: TimeWindow) { _window.value = tw }

    fun setPredictionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.apiSettings.first()
            settingsRepository.saveApiSettings(
                current.copy(
                    localModelSettings = current.localModelSettings.copy(
                        statisticsPrediction = enabled
                    )
                )
            )
        }
    }

    /** Recompute local progress-prediction coefficients from the gathered sessions. */
    fun recalculate() {
        viewModelScope.launch {
            _predictionStatus.value = PredictionUiState.Running
            _predictionStatus.value = PredictionUiState.Done(
                settingsRepository.recalculateLocalPrediction()
            )
        }
    }

    fun exportJsonl(onResult: (File?) -> Unit) {
        viewModelScope.launch {
            val f = perf.exportJsonl()
            onResult(f)
        }
    }

    private fun computeSummaries(rows: List<SessionLatencyRow>): List<ModelSummary> {
        if (rows.isEmpty()) return emptyList()
        return rows
            .groupBy { it.modelId to it.sessionType }
            .map { (key, list) ->
                val (modelId, sessionType) = key
                val durations = list.map { it.totalWallMs }
                val totalAudio = list.sumOf { it.audioDurationMs }
                val totalWall = list.sumOf { it.totalWallMs }
                val coldBreakdown = list.groupBy { it.coldStartKind }
                    .mapValues { (_, sub) ->
                        ColdBucket(sub.size, percentile(sub.map { it.totalWallMs }, 0.5))
                    }
                ModelSummary(
                    modelId = modelId,
                    sessionType = sessionType,
                    count = list.size,
                    successCount = list.count { it.success },
                    meanWallMs = if (durations.isEmpty()) 0L
                                 else (durations.sum() / durations.size),
                    p50WallMs = percentile(durations, 0.5),
                    p95WallMs = percentile(durations, 0.95),
                    audioToWallRatio = if (totalWall > 0)
                        totalAudio.toDouble() / totalWall.toDouble() else 0.0,
                    coldStartBreakdown = coldBreakdown
                )
            }
            .sortedByDescending { it.count }
    }

    private fun percentile(xs: List<Long>, p: Double): Long {
        if (xs.isEmpty()) return 0L
        val sorted = xs.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
