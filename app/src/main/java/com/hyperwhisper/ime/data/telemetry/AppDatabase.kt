package com.hyperwhisper.data.telemetry

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SessionEntity::class, SessionPhaseEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(TelemetryConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionSessionDao(): TranscriptionSessionDao
}
