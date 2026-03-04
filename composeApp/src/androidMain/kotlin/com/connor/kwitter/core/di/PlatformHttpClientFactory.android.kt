package com.connor.kwitter.core.di

import android.content.Context
import android.util.Log
import com.connor.cronet.engine.Cronet
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import org.koin.mp.KoinPlatform

private const val TAG = "PlatformHttpClient"

internal actual fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    val mode = KoinPlatform.getKoin()
        .getProperty<String>(NETWORK_ENGINE_PROPERTY_KEY)
        ?.trim()
        ?.lowercase()
        ?: NETWORK_ENGINE_CRONET

    return when (mode) {
        NETWORK_ENGINE_CIO -> {
            Log.i(TAG, "Using Ktor CIO engine [mode=$mode]")
            HttpClient(CIO) { configure() }
        }

        else -> createCronetClientOrFallback(configure, requestedMode = mode)
    }
}

private fun createCronetClientOrFallback(
    configure: HttpClientConfig<*>.() -> Unit,
    requestedMode: String,
): HttpClient {
    val appContext = runCatching { KoinPlatform.getKoin().get<Context>() }.getOrNull()
    if (appContext == null) {
        Log.w(TAG, "Android Context unavailable for Cronet, fallback to CIO [mode=$requestedMode]")
        return HttpClient(CIO) { configure() }
    }

    return runCatching {
        HttpClient(Cronet) {
            engine {
                context(appContext)
                enableQuic = false
            }
            configure()
        }.also {
            Log.i(TAG, "Using Cronet engine [mode=$requestedMode]")
        }
    }.getOrElse { cause ->
        Log.w(TAG, "Cronet init failed, fallback to CIO [mode=$requestedMode]", cause)
        HttpClient(CIO) { configure() }
    }
}
