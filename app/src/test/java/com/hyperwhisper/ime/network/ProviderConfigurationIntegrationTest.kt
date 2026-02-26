package com.hyperwhisper.network

import com.hyperwhisper.data.ApiProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ProviderConfigurationIntegrationTest {

    @Test
    fun transcriptionEndpointIntegration_reportsSuccessAndPerformanceForEveryProvider() = runBlocking {
        val report = linkedMapOf<ApiProvider, Long>()

        ApiProvider.entries.forEach { provider ->
            val server = MockWebServer()
            try {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"text":"ok-${provider.name.lowercase()}","duration":0.1}""")
                        .setHeader("Content-Type", "application/json")
                )
                server.start()

                val retrofit = Retrofit.Builder()
                    .baseUrl(server.url("/v1/"))
                    .client(
                        OkHttpClient.Builder()
                            .callTimeout(10, TimeUnit.SECONDS)
                            .build()
                    )
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val service = retrofit.create(TranscriptionApiService::class.java)

                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    "test.wav",
                    ByteArray(64).toRequestBody("audio/wav".toMediaType())
                )
                val modelPart = provider.defaultModels.first().toRequestBody("text/plain".toMediaType())

                val startedAt = System.nanoTime()
                val response = service.transcribe(
                    file = filePart,
                    model = modelPart
                )
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                report[provider] = elapsedMs

                assertTrue("Expected success response for ${provider.name}", response.isSuccessful)
                assertEquals("ok-${provider.name.lowercase()}", response.body()?.text)

                val request = server.takeRequest(2, TimeUnit.SECONDS)
                assertEquals("/v1/audio/transcriptions", request?.path)
            } finally {
                server.shutdown()
            }
        }

        val summary = buildString {
            appendLine("Provider integration performance report:")
            report.forEach { (provider, elapsedMs) ->
                appendLine("- ${provider.displayName}: success, ${elapsedMs}ms")
            }
        }
        println(summary)
    }
}
