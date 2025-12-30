package com.hyperwhisper.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hyperwhisper.data.SettingsRepository
import com.hyperwhisper.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TranscriptionRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatCompletionRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): SettingsRepository = SettingsRepository(context, gson)

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        settingsRepository: SettingsRepository
    ): Interceptor {
        return Interceptor { chain ->
            val apiSettings = runBlocking { settingsRepository.apiSettings.first() }
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${apiSettings.getCurrentApiKey()}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Dynamic Retrofit builder based on settings
     */
    private fun createRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @TranscriptionRetrofit
    fun provideTranscriptionRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        settingsRepository: SettingsRepository
    ): Retrofit {
        val baseUrl = runBlocking {
            val settings = settingsRepository.apiSettings.first()
            settings.baseUrl.ifEmpty { "https://api.openai.com/v1/" }
        }
        return createRetrofit(okHttpClient, gson, baseUrl)
    }

    @Provides
    @Singleton
    @ChatCompletionRetrofit
    fun provideChatCompletionRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        settingsRepository: SettingsRepository
    ): Retrofit {
        val baseUrl = runBlocking {
            val settings = settingsRepository.apiSettings.first()
            settings.baseUrl.ifEmpty { "https://api.openai.com/v1/" }
        }
        return createRetrofit(okHttpClient, gson, baseUrl)
    }

    @Provides
    @Singleton
    fun provideTranscriptionApiService(
        @TranscriptionRetrofit retrofit: Retrofit
    ): TranscriptionApiService {
        return retrofit.create(TranscriptionApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideChatCompletionApiService(
        @ChatCompletionRetrofit retrofit: Retrofit
    ): ChatCompletionApiService {
        return retrofit.create(ChatCompletionApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTranscriptionStrategy(
        apiService: TranscriptionApiService,
        settingsRepository: SettingsRepository
    ): TranscriptionStrategy {
        return TranscriptionStrategy(apiService, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideChatCompletionStrategy(
        apiService: ChatCompletionApiService,
        settingsRepository: SettingsRepository
    ): ChatCompletionStrategy {
        return ChatCompletionStrategy(apiService, settingsRepository)
    }

    // Note: Local whisper.cpp providers moved to flavor-specific FlavorModule
    // - Local flavor: Provides real WhisperContext, AudioConverter, ModelRepository, LocalWhisperStrategy
    // - Cloud flavor: Provides stub LocalWhisperStrategy that returns errors
}
