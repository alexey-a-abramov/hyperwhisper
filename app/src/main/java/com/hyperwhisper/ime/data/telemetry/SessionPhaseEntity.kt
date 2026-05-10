package com.hyperwhisper.data.telemetry

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_phases",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class SessionPhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("session_id") val sessionId: String,
    @ColumnInfo("phase_name") val phaseName: String,
    val ordinal: Int,
    @ColumnInfo("duration_ms") val durationMs: Long
)
