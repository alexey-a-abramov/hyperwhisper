package com.hyperwhisper.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.hyperwhisper.audio.AudioRecorderManager
import com.hyperwhisper.audio.SoundManager
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.data.VoiceCommandProcessor
import com.hyperwhisper.network.VoiceRepository
import com.hyperwhisper.ui.KeyboardViewModel
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

    @Provides
    @Singleton
    fun provideSoundManager(
        @ApplicationContext context: Context
    ): SoundManager {
        return SoundManager(context)
    }

    @Provides
    fun provideViewModelFactory(
        @ApplicationContext context: Context,
        voiceRepository: VoiceRepository,
        voiceCommandProcessor: VoiceCommandProcessor,
        settingsRepository: SettingsRepository,
        soundManager: SoundManager,
        audioRecorderManager: AudioRecorderManager,
        modifierKeyState: com.hyperwhisper.ime.keyboard.ModifierKeyState
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(KeyboardViewModel::class.java)) {
                    return KeyboardViewModel(
                        context,
                        voiceRepository,
                        voiceCommandProcessor,
                        settingsRepository,
                        soundManager,
                        audioRecorderManager,
                        modifierKeyState
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
