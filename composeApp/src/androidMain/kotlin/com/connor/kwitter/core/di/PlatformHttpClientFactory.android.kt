package com.connor.kwitter.core.di

import android.content.Context
import android.util.Log
import com.connor.cronet.engine.Cronet
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import org.koin.mp.KoinPlatform

private const val TAG = "PlatformHttpClient"

internal actual fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    val appContext = runCatching { KoinPlatform.getKoin().get<Context>() }
        .getOrElse { cause ->
            throw IllegalStateException(
                "Android Context is required before creating Cronet HttpClient.",
                cause,
            )
        }

    return runCatching {
        HttpClient(Cronet) {
            engine {
                context(appContext)
                enableQuic = false
            }
            configure()
        }.also {
            Log.i(TAG, "Using Cronet engine [mode=cronet]")
        }
    }.getOrElse { cause ->
        throw IllegalStateException("Cronet initialization failed.", cause)
    }
}
