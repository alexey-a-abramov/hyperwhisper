package com.hyperwhisper.data.telemetry

import androidx.room.ColumnInfo

/**
 * Narrow projection of [SessionEntity] used to fit progress-prediction
 * coefficients: just audio length and the resulting wall-clock per session.
 */
data class CalibrationRow(
    @ColumnInfo("model_id") val modelId: String,
    @ColumnInfo("audio_duration_ms") val audioDurationMs: Long,
    @ColumnInfo("total_wall_ms") val totalWallMs: Long,
)
