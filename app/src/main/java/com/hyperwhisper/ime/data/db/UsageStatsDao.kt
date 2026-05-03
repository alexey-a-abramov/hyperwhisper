package com.hyperwhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageStatsDao {

    // -- Per-model token usage --

    @Query("SELECT * FROM usage_stats_per_model")
    fun observePerModel(): Flow<List<ModelUsageEntity>>

    @Query("SELECT * FROM usage_stats_per_model")
    suspend fun getPerModel(): List<ModelUsageEntity>

    @Query("SELECT * FROM usage_stats_per_model WHERE modelId = :modelId")
    suspend fun getForModel(modelId: String): ModelUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelUsage(entity: ModelUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelUsageAll(entities: List<ModelUsageEntity>)

    @Query("DELETE FROM usage_stats_per_model")
    suspend fun deleteAllPerModel()

    // -- Cross-session totals --

    @Query("SELECT * FROM usage_stats_totals WHERE id = ${UsageTotalsEntity.SINGLETON_ID}")
    fun observeTotals(): Flow<UsageTotalsEntity?>

    @Query("SELECT * FROM usage_stats_totals WHERE id = ${UsageTotalsEntity.SINGLETON_ID}")
    suspend fun getTotals(): UsageTotalsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTotals(entity: UsageTotalsEntity)

    @Query("DELETE FROM usage_stats_totals")
    suspend fun deleteTotals()
}
