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

    private var localWhisperStrategyInstance: LocalWhisperStrategyStub? = null

    @Provides
    @Singleton
    fun provideLocalWhisperStrategyConcrete(): LocalWhisperStrategyStub {
        return localWhisperStrategyInstance ?: LocalWhisperStrategyStub().also { localWhisperStrategyInstance = it }
    }

    @Provides
    @Singleton
    fun provideLocalWhisperCallbacks(stub: LocalWhisperStrategyStub): LocalWhisperCallbacks {
        return stub
    }

    @Provides
    @Singleton
    @Named("localWhisperStrategy")
    fun provideLocalWhisperStrategy(stub: LocalWhisperStrategyStub): AudioProcessingStrategy {
        return stub
    }

    @Provides
    @Singleton
    @Named("isLocalFlavorEnabled")
    fun provideIsLocalFlavorEnabled(): Boolean = false
}
