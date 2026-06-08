package com.hyperwhisper.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fakes for the Room DAOs.
 *
 * Tests focus on the *migrator* logic and the repository's behaviour layered on
 * top of a DAO interface — they don't try to second-guess Room's SQL. The fakes
 * implement just enough surface to support every entry point the production
 * code uses.
 */

class FakeHistoryDao : HistoryDao {
    private val rows = MutableStateFlow<List<HistoryEntity>>(emptyList())

    override fun observeAll(): Flow<List<HistoryEntity>> = rows.asStateFlow()

    override suspend fun getAll(): List<HistoryEntity> =
        rows.value.sortedByDescending { it.timestamp }

    override suspend fun count(): Int = rows.value.size

    override suspend fun upsert(item: HistoryEntity) {
        rows.value = (rows.value.filterNot { it.id == item.id } + item)
            .sortedByDescending { it.timestamp }
    }

    override suspend fun upsertAll(items: List<HistoryEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun updateText(id: String, newText: String, newTimestamp: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(text = newText, timestamp = newTimestamp) else it
        }.sortedByDescending { it.timestamp }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun staleAfterTrim(keep: Int): List<HistoryEntity> =
        rows.value.sortedByDescending { it.timestamp }.drop(keep)

    override suspend fun trimToSize(keep: Int) {
        rows.value = rows.value.sortedByDescending { it.timestamp }.take(keep)
    }
}

class FakeApiCallLogDao : ApiCallLogDao {
    private val rows = MutableStateFlow<List<ApiCallLogEntity>>(emptyList())

    override fun observeAll(): Flow<List<ApiCallLogEntity>> = rows.asStateFlow()

    override suspend fun getAll(): List<ApiCallLogEntity> =
        rows.value.sortedByDescending { it.timestamp }

    override suspend fun upsert(log: ApiCallLogEntity) {
        rows.value = (rows.value.filterNot { it.id == log.id } + log)
            .sortedByDescending { it.timestamp }
    }

    override suspend fun upsertAll(logs: List<ApiCallLogEntity>) {
        logs.forEach { upsert(it) }
    }

    override suspend fun trimByProviderModel(provider: String, modelId: String, keep: Int) {
        val matches = rows.value
            .filter { it.provider == provider && it.modelId == modelId }
            .sortedByDescending { it.timestamp }
        val toKeep = matches.take(keep).map { it.id }.toSet()
        rows.value = rows.value.filterNot { row ->
            row.provider == provider && row.modelId == modelId && row.id !in toKeep
        }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun count(): Int = rows.value.size

    override suspend fun recentSuccessfulFor(
        provider: String,
        modelId: String,
        requestType: String,
        limit: Int,
    ): List<ApiCallLogEntity> =
        rows.value
            .filter {
                it.provider == provider && it.modelId == modelId &&
                    it.requestType == requestType && it.success && it.inputSize > 0
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
}

class FakeUsageStatsDao : UsageStatsDao {
    private val perModel = MutableStateFlow<List<ModelUsageEntity>>(emptyList())
    private val totals = MutableStateFlow<UsageTotalsEntity?>(null)

    override fun observePerModel(): Flow<List<ModelUsageEntity>> = perModel.asStateFlow()

    override suspend fun getPerModel(): List<ModelUsageEntity> = perModel.value

    override suspend fun getForModel(modelId: String): ModelUsageEntity? =
        perModel.value.firstOrNull { it.modelId == modelId }

    override suspend fun upsertModelUsage(entity: ModelUsageEntity) {
        perModel.value = perModel.value.filterNot { it.modelId == entity.modelId } + entity
    }

    override suspend fun upsertModelUsageAll(entities: List<ModelUsageEntity>) {
        entities.forEach { upsertModelUsage(it) }
    }

    override suspend fun deleteAllPerModel() {
        perModel.value = emptyList()
    }

    override fun observeTotals(): Flow<UsageTotalsEntity?> = totals.asStateFlow()

    override suspend fun getTotals(): UsageTotalsEntity? = totals.value

    override suspend fun upsertTotals(entity: UsageTotalsEntity) {
        totals.value = entity
    }

    override suspend fun deleteTotals() {
        totals.value = null
    }
}
