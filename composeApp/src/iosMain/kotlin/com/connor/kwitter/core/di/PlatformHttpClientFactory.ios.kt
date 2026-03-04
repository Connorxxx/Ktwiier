package com.connor.kwitter.core.di

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    return HttpClient(Darwin) {
        configure()
    }
}
