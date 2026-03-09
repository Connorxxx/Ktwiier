package com.connor.cronet.engine.internal.request

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.coroutines.CoroutineContext

internal interface CronetRequestExecutor : AutoCloseable {
    suspend fun execute(
        data: HttpRequestData,
        callContext: CoroutineContext,
        lifecycleHandle: CronetRequestLifecycleHandle,
    ): HttpResponseData

    override fun close() = Unit
}

internal interface CronetRequestLifecycleHandle {
    fun bindTransportCanceler(canceler: (Throwable?) -> Unit)

    /**
     * Cronet terminal callback received (onSucceeded/onFailed/onCanceled).
     * Unregisters the request from engine lifecycle tracking.
     */
    fun markTransportTerminal()

    /**
     * Body fully drained and response delivered to caller.
     * Used for telemetry/cleanup of the delivery phase.
     */
    fun markDeliveryComplete()
}
