package com.hyperwhisper.data.telemetry

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithPhases(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "session_id"
    )
    val phases: List<SessionPhaseEntity>
)
