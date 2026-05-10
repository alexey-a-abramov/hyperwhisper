package com.hyperwhisper.data.telemetry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["model_id", "started_at"]),
        Index(value = ["session_type", "started_at"]),
        Index(value = ["started_at"])
    ]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("started_at") val startedAt: Long,
    @ColumnInfo("session_type") val sessionType: SessionType,
    val provider: String,
    @ColumnInfo("model_id") val modelId: String,
    @ColumnInfo("audio_duration_ms") val audioDurationMs: Long,
    @ColumnInfo("voiced_ms") val voicedMs: Long?,
    @ColumnInfo("audio_sample_rate_hz") val audioSampleRateHz: Int?,
    @ColumnInfo("audio_channels") val audioChannels: Int?,
    @ColumnInfo("output_chars") val outputChars: Int,
    @ColumnInfo("output_tokens") val outputTokens: Int?,
    @ColumnInfo("input_tokens") val inputTokens: Int?,
    @ColumnInfo("total_tokens") val totalTokens: Int?,
    @ColumnInfo("input_language") val inputLanguage: String?,
    @ColumnInfo("detected_language") val detectedLanguage: String?,
    @ColumnInfo("total_wall_ms") val totalWallMs: Long,
    val success: Boolean,
    @ColumnInfo("error_kind") val errorKind: String?,
    @ColumnInfo("cold_start_kind") val coldStartKind: ColdStartKind,
    @ColumnInfo("thermal_status") val thermalStatus: Int?,
    @ColumnInfo("battery_pct") val batteryPct: Int?,
    @ColumnInfo("battery_charging") val batteryCharging: Boolean?,
    @ColumnInfo("network_type") val networkType: NetworkType,
    @ColumnInfo("device_model") val deviceModel: String,
    @ColumnInfo("os_version") val osVersion: String,
    @ColumnInfo("app_version_code") val appVersionCode: Int,
    @ColumnInfo("model_size_bytes") val modelSizeBytes: Long?,
    @ColumnInfo("retry_of") val retryOf: String?
)
