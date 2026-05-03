package com.hyperwhisper.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        ApiCallLogEntity::class,
        ModelUsageEntity::class,
        UsageTotalsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HyperWhisperDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun apiCallLogDao(): ApiCallLogDao
    abstract fun usageStatsDao(): UsageStatsDao
}
