package com.hyperwhisper.data.telemetry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<SessionPhaseEntity>)

    @Transaction
    suspend fun insertSessionWithPhases(session: SessionEntity, phases: List<SessionPhaseEntity>) {
        insertSession(session)
        if (phases.isNotEmpty()) insertPhases(phases)
    }

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY started_at DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<SessionWithPhases>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE started_at >= :sinceEpochMs ORDER BY started_at DESC")
    suspend fun allSince(sinceEpochMs: Long): List<SessionWithPhases>

    @Query("""
        SELECT model_id, session_type, cold_start_kind, total_wall_ms, audio_duration_ms, success
          FROM sessions
         WHERE started_at >= :sinceEpochMs
    """)
    fun latencyRowsSince(sinceEpochMs: Long): Flow<List<SessionLatencyRow>>

    @Query("""
        SELECT model_id, audio_duration_ms, total_wall_ms
          FROM sessions
         WHERE session_type = :type AND success = 1 AND audio_duration_ms > 0
    """)
    suspend fun calibrationRows(type: SessionType): List<CalibrationRow>

    @Query("DELETE FROM sessions WHERE started_at < :cutoffEpochMs")
    suspend fun pruneOlderThan(cutoffEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM sessions")
    fun totalCount(): Flow<Int>
}
