package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import org.chromium.net.CronetEngine

internal class DefaultCronetRequestExecutor(
    private val cronetEngine: CronetEngine,
    private val callbackExecutor: Executor,
    private val telemetry: CronetTelemetry,
) : CronetRequestExecutor {

    override suspend fun execute(
        data: HttpRequestData,
        callContext: CoroutineContext,
    ): HttpResponseData {
        TODO(
            "Step 2: request/response bridge. " +
                "Map HttpRequestData -> UrlRequest, create ByteReadChannel pump, " +
                "wire cancellation, then adapt response via ResponseAdapterAttributeKey."
        )
    }
}
