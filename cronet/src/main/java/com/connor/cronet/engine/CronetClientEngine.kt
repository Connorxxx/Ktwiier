package com.connor.cronet.engine

import com.connor.cronet.engine.internal.CronetEngineFactory
import com.connor.cronet.engine.internal.request.CronetRequestExecutor
import com.connor.cronet.engine.internal.request.DefaultCronetRequestExecutor
import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import com.connor.cronet.engine.internal.telemetry.NoopCronetTelemetry
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private val requestExecutor: CronetRequestExecutor = DefaultCronetRequestExecutor(
        cronetEngine = cronetEngine,
        callbackExecutor = callbackExecutor,
        telemetry = telemetry,
    )

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        return requestExecutor.execute(data = data, callContext = callContext())
    }

    override fun close() {
        super.close()

        requestExecutor.close()
        callbackExecutor.shutdown()

        runCatching { cronetEngine.shutdown() }.onFailure(telemetry::onEngineShutdownFailure)
    }
}
