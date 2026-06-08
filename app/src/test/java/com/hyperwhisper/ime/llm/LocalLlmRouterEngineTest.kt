package com.hyperwhisper.ime.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for local-LLM engine selection by model-file extension. This is
 * the "local mode" routing decision (.gguf → llama.cpp, everything else →
 * MediaPipe Gemma) — testable without loading any native library because it
 * lives on the companion.
 */
class LocalLlmRouterEngineTest {

    @Test
    fun ggufRoutesToLlamaCpp() {
        assertEquals(LocalLlmRouter.Engine.LLAMA_CPP, LocalLlmRouter.engineFor("/models/qwen.gguf"))
    }

    @Test
    fun ggufIsCaseInsensitive() {
        assertEquals(LocalLlmRouter.Engine.LLAMA_CPP, LocalLlmRouter.engineFor("/models/Qwen.GGUF"))
    }

    @Test
    fun mediaPipeExtensionsRouteToGemma() {
        assertEquals(LocalLlmRouter.Engine.GEMMA, LocalLlmRouter.engineFor("/models/gemma.task"))
        assertEquals(LocalLlmRouter.Engine.GEMMA, LocalLlmRouter.engineFor("/models/gemma.litertlm"))
        assertEquals(LocalLlmRouter.Engine.GEMMA, LocalLlmRouter.engineFor("/models/gemma.bin"))
    }

    @Test
    fun unknownOrNoExtensionDefaultsToGemma() {
        assertEquals(LocalLlmRouter.Engine.GEMMA, LocalLlmRouter.engineFor("/models/model"))
        assertEquals(LocalLlmRouter.Engine.GEMMA, LocalLlmRouter.engineFor(""))
    }
}
