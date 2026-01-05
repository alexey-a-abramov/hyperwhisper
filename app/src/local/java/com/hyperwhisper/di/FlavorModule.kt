package com.hyperwhisper.di

import android.content.Context
import com.hyperwhisper.data.ModelRepository
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.native_whisper.AudioConverter
import com.hyperwhisper.native_whisper.WhisperContext
import com.hyperwhisper.network.AudioProcessingStrategy
import com.hyperwhisper.network.LocalWhisperCallbacks
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * Flavor-specific Hilt module for LOCAL flavor
 * Provides native whisper.cpp dependencies and local processing strategy
 */
@Module
@InstallIn(SingletonComponent::class)
object FlavorModule {

    @Provides
    @Singleton
    fun provideWhisperContext(): WhisperContext {
        return WhisperContext()
    }

    @Provides
    @Singleton
    fun provideAudioConverter(): AudioConverter {
        return AudioConverter()
    }

    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ModelRepository {
        return ModelRepository(context, okHttpClient)
    }

    // First, create the singleton instance
    // Use the interface as type to avoid KSP issues in cloud builds
    private var localWhisperCallbacksInstance: LocalWhisperCallbacks? = null

    @Provides
    @Singleton
    fun provideLocalWhisperCallbacks(
        whisperContext: WhisperContext,
        audioConverter: AudioConverter,
        modelRepository: ModelRepository,
        settingsRepository: SettingsRepository
    ): LocalWhisperCallbacks {
        return localWhisperCallbacksInstance ?: com.hyperwhisper.network.LocalWhisperStrategy(
            whisperContext,
            audioConverter,
            modelRepository,
            settingsRepository
        ).also { localWhisperCallbacksInstance = it }
    }

    @Provides
    @Singleton
    @Named("localWhisperStrategy")
    fun provideLocalWhisperStrategy(
        callbacks: LocalWhisperCallbacks
    ): AudioProcessingStrategy {
        return callbacks as AudioProcessingStrategy
    }

    @Provides
    @Singleton
    @Named("isLocalFlavorEnabled")
    fun provideIsLocalFlavorEnabled(): Boolean = true
}
