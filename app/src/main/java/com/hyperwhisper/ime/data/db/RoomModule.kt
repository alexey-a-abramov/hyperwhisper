package com.hyperwhisper.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HyperWhisperDatabase =
        Room.databaseBuilder(
            context,
            HyperWhisperDatabase::class.java,
            "hyperwhisper.db",
        )
            // Schema is small and migrations during early Phase-2 development
            // are unlikely. If a future change requires preserving data, add an
            // explicit Migration object instead of swapping this for
            // fallbackToDestructiveMigration.
            .build()

    @Provides
    @Singleton
    fun provideHistoryDao(db: HyperWhisperDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideApiCallLogDao(db: HyperWhisperDatabase): ApiCallLogDao = db.apiCallLogDao()

    @Provides
    @Singleton
    fun provideUsageStatsDao(db: HyperWhisperDatabase): UsageStatsDao = db.usageStatsDao()
}
