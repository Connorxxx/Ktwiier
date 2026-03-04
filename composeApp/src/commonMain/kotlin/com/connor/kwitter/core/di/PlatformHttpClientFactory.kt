package com.connor.kwitter.core.di

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

const val NETWORK_ENGINE_PROPERTY_KEY = "network.http.engine"
const val NETWORK_ENGINE_CRONET = "cronet"
const val NETWORK_ENGINE_CIO = "cio"

internal expect fun createPlatformHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient
