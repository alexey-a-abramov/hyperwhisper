package com.hyperwhisper.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hyperwhisper.data.*
import com.hyperwhisper.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

    // ApiSettingsRepository is now provided via constructor injection
    // (@Inject constructor in ApiSettingsRepository.kt). The DataStore it
    // depends on is supplied via the @ApiSettingsDataStore qualifier in
    // ApiSettingsDataStoreModule.

    @Provides
    @Singleton
    fun provideVoiceModesRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): VoiceModesRepository = VoiceModesRepository(context, gson)

    @Provides
    @Singleton
    fun provideAppearanceRepository(
        @ApplicationContext context: Context
    ): AppearanceRepository = AppearanceRepository(context)

    @Provides
    @Singleton
    fun provideUsageStatisticsRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): UsageStatisticsRepository = UsageStatisticsRepository(context, gson)

    @Provides
    @Singleton
    fun provideHistoryRepository(
        @ApplicationContext context: Context,
        gson: Gson,
        appearanceRepository: AppearanceRepository
    ): HistoryRepository = HistoryRepository(context, gson, appearanceRepository)

    @Provides
    @Singleton
    fun provideLanguageTrackingRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): LanguageTrackingRepository = LanguageTrackingRepository(context, gson)

    @Provides
    @Singleton
    fun provideProviderModelTrackingRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): ProviderModelTrackingRepository = ProviderModelTrackingRepository(context, gson)

    @Provides
    @Singleton
    fun provideApiCallLogRepository(
        @ApplicationContext context: Context
    ): ApiCallLogRepository = ApiCallLogRepository(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        apiSettingsRepository: ApiSettingsRepository,
        voiceModesRepository: VoiceModesRepository,
        appearanceRepository: AppearanceRepository,
        usageStatisticsRepository: UsageStatisticsRepository,
        historyRepository: HistoryRepository,
        languageTrackingRepository: LanguageTrackingRepository,
        providerModelTrackingRepository: ProviderModelTrackingRepository,
        apiCallLogRepository: ApiCallLogRepository,
        localModelRepository: com.hyperwhisper.data.LocalModelRepository
    ): SettingsRepository = SettingsRepository(
        apiSettingsRepository,
        voiceModesRepository,
        appearanceRepository,
        usageStatisticsRepository,
        historyRepository,
        languageTrackingRepository,
        providerModelTrackingRepository,
        apiCallLogRepository,
        localModelRepository
    )

    @Provides
    @Singleton
    fun provideLocalProcessingStrategy(
        settingsRepository: SettingsRepository,
        whisperCache: com.hyperwhisper.ime.whisper.WhisperContextCache,
        gemma: com.hyperwhisper.ime.llm.GemmaInferenceEngine
    ): com.hyperwhisper.network.LocalProcessingStrategy {
        return com.hyperwhisper.network.LocalProcessingStrategy(settingsRepository, whisperCache, gemma)
    }

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
        apiSettingsRepository: ApiSettingsRepository
    ): Interceptor {
        return Interceptor { chain ->
            // Read from cached snapshot — no blocking. The snapshot is kept current
            // by an internal coroutine in ApiSettingsRepository.
            val apiSettings = apiSettingsRepository.snapshot()
            val requestBuilder = chain.request().newBuilder()

            if (apiSettings.getCurrentRequiresAuth()) {
                requestBuilder.addHeader("Authorization", "Bearer ${apiSettings.getCurrentApiKey()}")
            }

            requestBuilder.addHeader("Content-Type", "application/json")
            chain.proceed(requestBuilder.build())
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
            .connectTimeout(30, TimeUnit.SECONDS)    // Connection establishment
            .readTimeout(180, TimeUnit.SECONDS)      // Increased for longer audio processing (3 min)
            .writeTimeout(60, TimeUnit.SECONDS)      // Upload time
            .callTimeout(300, TimeUnit.SECONDS)      // Total call timeout (5 min)
            .followRedirects(true)
            .followSslRedirects(true)
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
        // Bypassing "local" or invalid URLs for Retrofit as it requires a valid schema
        val finalUrl = if (baseUrl == "local" || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
            "http://localhost/" 
        } else {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        }
        
        return Retrofit.Builder()
            .baseUrl(finalUrl)
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
        apiSettingsRepository: ApiSettingsRepository
    ): Retrofit {
        // Use cached snapshot — non-blocking. Falls back to provider default
        // until the first DataStore emission lands.
        return createRetrofit(okHttpClient, gson, apiSettingsRepository.snapshot().getCurrentBaseUrl())
    }

    @Provides
    @Singleton
    @ChatCompletionRetrofit
    fun provideChatCompletionRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        apiSettingsRepository: ApiSettingsRepository
    ): Retrofit {
        return createRetrofit(okHttpClient, gson, apiSettingsRepository.snapshot().getCurrentBaseUrl())
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
}
