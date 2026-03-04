package com.connor.cronet.engine.internal

import android.content.Context
import com.connor.cronet.engine.CronetEngineConfig
import com.connor.cronet.engine.CronetHttpCache
import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import org.chromium.net.CronetEngine

internal class CronetEngineFactory(
    private val providerSelector: CronetProviderSelector = DefaultCronetProviderSelector,
) {

    fun create(
        appContext: Context,
        config: CronetEngineConfig,
        telemetry: CronetTelemetry,
    ): CronetEngine {
        val provider = providerSelector.select(
            context = appContext,
            strategy = config.providerStrategy,
        )

        telemetry.onProviderSelected(
            providerName = provider.name,
            providerVersion = provider.version,
        )

        return provider.createBuilder()
            .enableHttp2(config.enableHttp2)
            .enableQuic(config.enableQuic)
            .enableBrotli(config.enableBrotli)
            .apply {
                config.userAgent?.let(::setUserAgent)
                applyHttpCache(
                    cache = config.httpCache,
                    storagePath = config.storagePath,
                )
            }
            .build()
    }

    private fun CronetEngine.Builder.applyHttpCache(
        cache: CronetHttpCache,
        storagePath: String?,
    ) {
        when (cache) {
            CronetHttpCache.Disabled -> {
                enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISABLED, 0L)
            }

            is CronetHttpCache.InMemory -> {
                enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, cache.maxSizeBytes)
            }

            is CronetHttpCache.DiskNoHttp -> {
                require(!storagePath.isNullOrBlank()) {
                    "Cronet disk cache requires storagePath"
                }
                setStoragePath(storagePath)
                enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, cache.maxSizeBytes)
            }

            is CronetHttpCache.Disk -> {
                require(!storagePath.isNullOrBlank()) {
                    "Cronet disk cache requires storagePath"
                }
                setStoragePath(storagePath)
                enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, cache.maxSizeBytes)
            }
        }
    }
}
