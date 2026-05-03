package com.hyperwhisper.network

import com.google.gson.Gson
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a [ChatCompletionApiService] for a specific [LlmConfig]. Each call
 * returns a fresh Retrofit/OkHttp pair — there is no caching, mirroring the
 * previous private factory inside [VoiceRepository].
 *
 * Provider-specific auth headers (Anthropic vs. OpenAI-compatible Bearer) are
 * applied via a per-call interceptor.
 */
@Singleton
class LlmServiceFactory @Inject constructor(
    private val gson: Gson
) {
    fun create(llmConfig: LlmConfig): ChatCompletionApiService {
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()

            if (llmConfig.requiresAuth && llmConfig.apiKey.isNotEmpty()) {
                when (llmConfig.provider) {
                    LlmProvider.ANTHROPIC -> {
                        // Anthropic uses x-api-key header
                        requestBuilder.addHeader("x-api-key", llmConfig.apiKey)
                        requestBuilder.addHeader("anthropic-version", "2023-06-01")
                    }
                    else -> {
                        // OpenAI-compatible providers use Bearer token
                        requestBuilder.addHeader("Authorization", "Bearer ${llmConfig.apiKey}")
                    }
                }
            }

            requestBuilder.addHeader("Content-Type", "application/json")
            chain.proceed(requestBuilder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(llmConfig.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(ChatCompletionApiService::class.java)
    }
}
