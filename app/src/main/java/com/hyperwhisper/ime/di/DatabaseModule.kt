package com.hyperwhisper.di

import android.content.Context
import androidx.room.Room
import com.hyperwhisper.data.telemetry.AppDatabase
import com.hyperwhisper.data.telemetry.TranscriptionSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, "hyperwhisper_telemetry.db")
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideTranscriptionSessionDao(db: AppDatabase): TranscriptionSessionDao =
        db.transcriptionSessionDao()
}
