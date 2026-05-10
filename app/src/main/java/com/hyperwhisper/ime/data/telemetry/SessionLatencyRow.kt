package com.hyperwhisper.data.telemetry

import androidx.room.ColumnInfo

data class SessionLatencyRow(
    @ColumnInfo("model_id") val modelId: String,
    @ColumnInfo("session_type") val sessionType: SessionType,
    @ColumnInfo("cold_start_kind") val coldStartKind: ColdStartKind,
    @ColumnInfo("total_wall_ms") val totalWallMs: Long,
    @ColumnInfo("audio_duration_ms") val audioDurationMs: Long,
    val success: Boolean
)
