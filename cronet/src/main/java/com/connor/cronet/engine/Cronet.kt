package com.connor.cronet.engine

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Ktor entry point for the custom Cronet engine.
 *
 * Usage:
 * `HttpClient(Cronet) { engine { context(appContext) } }`
 */
data object Cronet : HttpClientEngineFactory<CronetEngineConfig> {
    override fun create(block: CronetEngineConfig.() -> Unit): HttpClientEngine {
        return CronetClientEngine(CronetEngineConfig().apply(block))
    }
}
