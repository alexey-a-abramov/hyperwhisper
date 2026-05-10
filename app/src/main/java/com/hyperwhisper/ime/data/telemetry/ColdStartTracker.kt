package com.hyperwhisper.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColdStartTracker @Inject constructor() {
    private val seenModels = HashSet<String>()
    private val lock = Any()

    fun classify(modelId: String): ColdStartKind = synchronized(lock) {
        val kind = when {
            seenModels.isEmpty() -> ColdStartKind.PROCESS_COLD
            modelId !in seenModels -> ColdStartKind.MODEL_COLD
            else -> ColdStartKind.WARM
        }
        seenModels.add(modelId)
        kind
    }
}
