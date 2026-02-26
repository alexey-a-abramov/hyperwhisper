package com.hyperwhisper.ui.about

import com.google.gson.Gson
import com.hyperwhisper.data.ApiProvider
import com.hyperwhisper.data.ApiSettings
import com.hyperwhisper.network.TranscriptionApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

data class ProviderIntegrationResult(
    val provider: ApiProvider,
    val configured: Boolean,
    val success: Boolean,
    val durationMs: Long,
    val statusCode: Int? = null,
    val message: String
)

class ProviderIntegrationTestRunner(
    private val gson: Gson
) {
    suspend fun runAll(apiSettings: ApiSettings): List<ProviderIntegrationResult> = withContext(Dispatchers.IO) {
        ApiProvider.entries.map { provider ->
            runSingleProvider(provider, apiSettings)
        }
    }

    private suspend fun runSingleProvider(
        provider: ApiProvider,
        apiSettings: ApiSettings
    ): ProviderIntegrationResult {
        val providerConfig = apiSettings.providerConfigs[provider]
        val requiresAuth = providerConfig?.requiresAuth ?: provider.requiresAuth
        val apiKey = apiSettings.apiKeys[provider].orEmpty()
        val modelId = if (apiSettings.provider == provider && apiSettings.modelId.isNotBlank()) {
            apiSettings.modelId
        } else {
            provider.defaultModels.firstOrNull() ?: "whisper-1"
        }
        val baseUrl = normalizeBaseUrl(
            providerConfig?.customBaseUrl?.ifBlank { provider.defaultEndpoint } ?: provider.defaultEndpoint
        )

        if (requiresAuth && apiKey.isBlank()) {
            return ProviderIntegrationResult(
                provider = provider,
                configured = false,
                success = false,
                durationMs = 0L,
                message = "Not configured (missing API key)"
            )
        }

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val reqBuilder = chain.request().newBuilder()
                    if (requiresAuth && apiKey.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                    }
                    chain.proceed(reqBuilder.build())
                }
                .build()

            val service = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(TranscriptionApiService::class.java)

            val filePart = MultipartBody.Part.createFormData(
                "file",
                "probe.wav",
                ByteArray(44).toRequestBody("audio/wav".toMediaType())
            )
            val modelPart = modelId.toRequestBody("text/plain".toMediaType())

            val startedAt = System.nanoTime()
            val response = service.transcribe(file = filePart, model = modelPart)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            ProviderIntegrationResult(
                provider = provider,
                configured = true,
                success = response.isSuccessful,
                durationMs = elapsedMs,
                statusCode = response.code(),
                message = if (response.isSuccessful) {
                    "Success"
                } else {
                    "HTTP ${response.code()}"
                }
            )
        } catch (e: Exception) {
            ProviderIntegrationResult(
                provider = provider,
                configured = true,
                success = false,
                durationMs = 0L,
                message = e.message ?: "Network error"
            )
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}
