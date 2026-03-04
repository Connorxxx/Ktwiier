package com.connor.cronet.engine.internal.request

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.coroutines.CoroutineContext

internal interface CronetRequestExecutor : AutoCloseable {
    suspend fun execute(
        data: HttpRequestData,
        callContext: CoroutineContext,
    ): HttpResponseData

    override fun close() = Unit
}
