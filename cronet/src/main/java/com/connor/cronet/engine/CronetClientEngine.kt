package com.connor.cronet.engine

import com.connor.cronet.engine.internal.CronetEngineFactory
import com.connor.cronet.engine.internal.lifecycle.ActiveRequestHandle
import com.connor.cronet.engine.internal.lifecycle.EngineLifecycle
import com.connor.cronet.engine.internal.request.CronetRequestExecutor
import com.connor.cronet.engine.internal.request.DefaultCronetRequestExecutor
import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import com.connor.cronet.engine.internal.telemetry.NoopCronetTelemetry
import io.ktor.client.engine.ClientEngineClosedException
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class)
internal class CronetClientEngine(
    override val config: CronetEngineConfig,
    private val telemetry: CronetTelemetry = NoopCronetTelemetry,
) : HttpClientEngineBase("ktor-cronet") {

    /**
     * Keep capabilities conservative until each capability is fully implemented and verified.
     */
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    init {
        config.validate()
    }

    private val callbackExecutor: ExecutorService = Executors.newFixedThreadPool(config.callbackThreadCount)

    private val cronetEngine = CronetEngineFactory().create(
        appContext = config.requireAppContext(),
        config = config,
        telemetry = telemetry,
    )

    private val lifecycle = EngineLifecycle()

    private val requestExecutor: CronetRequestExecutor = DefaultCronetRequestExecutor(
        cronetEngine = cronetEngine,
        callbackExecutor = callbackExecutor,
    )

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val requestCallContext = callContext()
        val requestId = lifecycle.registerActiveRequest(
            handle = CallContextRequestHandle(callContext = requestCallContext),
        )

        return try {
            requestExecutor.execute(
                data = data,
                callContext = requestCallContext,
            )
        } finally {
            lifecycle.unregisterActiveRequest(requestId)
        }
    }

    override fun close() {
        if (!lifecycle.startClosing()) {
            return
        }

        super.close()

        try {
            lifecycle.cancelAllActiveRequests(cause = ClientEngineClosedException())

            runCatching { requestExecutor.close() }
            runCatching { callbackExecutor.shutdown() }

            runCatching { cronetEngine.shutdown() }.onFailure(telemetry::onEngineShutdownFailure)
        } finally {
            lifecycle.markClosed()
        }
    }

    private class CallContextRequestHandle(callContext: CoroutineContext) : ActiveRequestHandle {
        private val callJob = checkNotNull(callContext[Job]) {
            "Ktor call context must include a Job"
        }

        override fun cancel(cause: Throwable?) {
            val cancellation = if (cause is CancellationException) {
                cause
            } else {
                CancellationException("Cronet client engine is closing").apply {
                    if (cause != null) {
                        initCause(cause)
                    }
                }
            }

            callJob.cancel(cancellation)
        }
    }
}
