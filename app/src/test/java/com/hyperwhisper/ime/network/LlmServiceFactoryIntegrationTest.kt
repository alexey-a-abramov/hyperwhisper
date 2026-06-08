package com.hyperwhisper.network

import com.google.gson.Gson
import com.hyperwhisper.data.ChatCompletionRequest
import com.hyperwhisper.data.ChatMessage
import com.hyperwhisper.data.ContentPart
import com.hyperwhisper.data.LlmConfig
import com.hyperwhisper.data.LlmProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the third-party LLM (chat-completions) path. Builds a
 * real [ChatCompletionApiService] via [LlmServiceFactory] pointed at a
 * MockWebServer, so the provider-specific auth interceptor and the request
 * wire format are exercised end-to-end (not mocked).
 */
class LlmServiceFactoryIntegrationTest {

    private val factory = LlmServiceFactory(Gson())

    private fun successBody() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"id":"cmpl-1","choices":[{"message":{"role":"assistant","content":"polished"},""" +
                """"finish_reason":"stop"}]}"""
        )

    private fun sampleRequest(model: String) = ChatCompletionRequest(
        model = model,
        messages = listOf(
            ChatMessage(role = "user", content = listOf(ContentPart.TextContent(text = "fix this")))
        )
    )

    /** Fire one chat completion against a fresh MockWebServer and hand the
     *  recorded request + parsed body to [assertions]. */
    private fun withCall(
        config: LlmConfig,
        block: (request: okhttp3.mockwebserver.RecordedRequest, content: String?) -> Unit
    ) = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(successBody())
            server.start()
            val pointed = config.copy(customBaseUrl = server.url("/v1/").toString())
            val service = factory.create(pointed)
            val response = service.chatCompletion(sampleRequest(pointed.modelId))
            assertTrue("expected 2xx", response.isSuccessful)
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
            block(recorded, response.body()?.choices?.firstOrNull()?.message?.content)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anthropic_usesXApiKeyHeader_notBearer() = withCall(
        LlmConfig(provider = LlmProvider.ANTHROPIC, apiKey = "sk-ant-123", requiresAuth = true)
    ) { request, content ->
        assertEquals("sk-ant-123", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertNull("Anthropic must not send a Bearer token", request.getHeader("Authorization"))
        assertEquals("polished", content)
    }

    @Test
    fun openAiCompatible_usesBearerToken() = withCall(
        LlmConfig(provider = LlmProvider.OPENAI, apiKey = "sk-oai-456", requiresAuth = true)
    ) { request, _ ->
        assertEquals("Bearer sk-oai-456", request.getHeader("Authorization"))
        assertNull(request.getHeader("x-api-key"))
    }

    @Test
    fun blankApiKey_sendsNoAuthHeader() = withCall(
        LlmConfig(provider = LlmProvider.OPENAI, apiKey = "", requiresAuth = true)
    ) { request, _ ->
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("x-api-key"))
    }

    @Test
    fun hitsChatCompletionsEndpoint_withJsonBody() = withCall(
        LlmConfig(provider = LlmProvider.OPENAI, apiKey = "k", modelId = "gpt-4o-mini", requiresAuth = true)
    ) { request, _ ->
        assertTrue("path was ${request.path}", request.path!!.endsWith("/chat/completions"))
        assertEquals("POST", request.method)
        // Retrofit's Gson converter may append "; charset=UTF-8" — match the prefix.
        assertTrue(
            "Content-Type was ${request.getHeader("Content-Type")}",
            request.getHeader("Content-Type")?.startsWith("application/json") == true
        )
        // body carries the configured model id
        assertTrue(request.body.readUtf8().contains("\"gpt-4o-mini\""))
    }
}
