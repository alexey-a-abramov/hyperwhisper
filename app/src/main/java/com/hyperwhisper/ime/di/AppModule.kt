package com.hyperwhisper.di

import android.content.Context
import com.hyperwhisper.audio.AudioRecorderManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context

    @Provides
    @Singleton
    fun provideAudioRecorderManager(
        @ApplicationContext context: Context
    ): AudioRecorderManager {
        return AudioRecorderManager(context)
    }
}
