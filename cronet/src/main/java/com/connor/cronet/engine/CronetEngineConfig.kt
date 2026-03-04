package com.connor.cronet.engine

import android.content.Context
import io.ktor.client.engine.HttpClientEngineConfig

class CronetEngineConfig : HttpClientEngineConfig() {
    private var appContext: Context? = null

    var providerStrategy: CronetProviderStrategy = CronetProviderStrategy.AppPackagedOnly
    var enableHttp2: Boolean = true
    var enableQuic: Boolean = true
    var enableBrotli: Boolean = true
    var userAgent: String? = null

    /**
     * Optional path used for disk cache modes.
     * Required when [httpCache] is [CronetHttpCache.Disk] or [CronetHttpCache.DiskNoHttp].
     */
    var storagePath: String? = null

    var httpCache: CronetHttpCache = CronetHttpCache.Disabled

    /**
     * Callback executor thread count for Cronet request callbacks.
     */
    var callbackThreadCount: Int = 2

    /**
     * Max wait time for active requests to drain after close() triggers cancellation.
     */
    var closeDrainTimeoutMillis: Long = DEFAULT_CLOSE_DRAIN_TIMEOUT_MILLIS

    /**
     * Additional wait time after a second forced cancel pass when initial drain times out.
     */
    var closeForceDrainTimeoutMillis: Long = DEFAULT_CLOSE_FORCE_DRAIN_TIMEOUT_MILLIS

    fun context(context: Context) {
        appContext = context.applicationContext
    }

    internal fun requireAppContext(): Context {
        return requireNotNull(appContext) {
            "CronetEngineConfig.context(context) is required. Pass application context."
        }
    }

    internal fun validate() {
        require(callbackThreadCount > 0) {
            "CronetEngineConfig.callbackThreadCount must be > 0"
        }
        require(closeDrainTimeoutMillis >= 0L) {
            "CronetEngineConfig.closeDrainTimeoutMillis must be >= 0"
        }
        require(closeForceDrainTimeoutMillis >= 0L) {
            "CronetEngineConfig.closeForceDrainTimeoutMillis must be >= 0"
        }
    }

    private companion object {
        const val DEFAULT_CLOSE_DRAIN_TIMEOUT_MILLIS: Long = 5_000L
        const val DEFAULT_CLOSE_FORCE_DRAIN_TIMEOUT_MILLIS: Long = 1_000L
    }
}

enum class CronetProviderStrategy {
    AppPackagedOnly,
    PreferAppPackaged,
    AutoBestAvailable,
}

sealed interface CronetHttpCache {
    data object Disabled : CronetHttpCache
    data class InMemory(val maxSizeBytes: Long) : CronetHttpCache
    data class DiskNoHttp(val maxSizeBytes: Long) : CronetHttpCache
    data class Disk(val maxSizeBytes: Long) : CronetHttpCache
}
