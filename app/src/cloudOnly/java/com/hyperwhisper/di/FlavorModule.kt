package com.hyperwhisper.di

import com.hyperwhisper.network.AudioProcessingStrategy
import com.hyperwhisper.network.LocalWhisperStrategyStub
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Flavor-specific Hilt module for CLOUD_ONLY flavor
 * Provides stub implementations for local whisper dependencies
 * Native whisper.cpp code is excluded from cloud builds
 */
@Module
@InstallIn(SingletonComponent::class)
object FlavorModule {

    @Provides
    @Singleton
    @Named("localWhisperStrategy")
    fun provideLocalWhisperStrategy(): AudioProcessingStrategy {
        // Return stub that returns errors when local processing is attempted
        return LocalWhisperStrategyStub()
    }

    @Provides
    @Singleton
    @Named("isLocalFlavorEnabled")
    fun provideIsLocalFlavorEnabled(): Boolean = false
}
