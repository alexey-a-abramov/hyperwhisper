package com.hyperwhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiCallLogDao {

    @Query("SELECT * FROM api_call_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ApiCallLogEntity>>

    @Query("SELECT * FROM api_call_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<ApiCallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ApiCallLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<ApiCallLogEntity>)

    /**
     * Per-(provider, model) rotation: keep the [keep] most recent rows for the
     * given combination, drop the rest. Implements
     * `ORDER BY timestamp DESC LIMIT :keep` semantics via a subquery so other
     * providers/models are untouched.
     */
    @Query(
        "DELETE FROM api_call_logs WHERE provider = :provider AND modelId = :modelId " +
            "AND id NOT IN (" +
            "  SELECT id FROM api_call_logs WHERE provider = :provider AND modelId = :modelId " +
            "  ORDER BY timestamp DESC LIMIT :keep" +
            ")",
    )
    suspend fun trimByProviderModel(provider: String, modelId: String, keep: Int)

    @Query("DELETE FROM api_call_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM api_call_logs")
    suspend fun count(): Int

    /**
     * Recent successful entries for a given (provider, model, requestType)
     * — used by the transcription progress estimator to compute an average
     * wall-clock-per-byte ratio. Filtered to success=1 so timeouts/aborts
     * don't skew the estimate. Trimmed by [limit] (typically ≤10) so the
     * average tracks recent conditions, not full history.
     */
    @Query(
        "SELECT * FROM api_call_logs " +
            "WHERE provider = :provider AND modelId = :modelId " +
            "AND requestType = :requestType AND success = 1 AND inputSize > 0 " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun recentSuccessfulFor(
        provider: String,
        modelId: String,
        requestType: String,
        limit: Int,
    ): List<ApiCallLogEntity>
}
