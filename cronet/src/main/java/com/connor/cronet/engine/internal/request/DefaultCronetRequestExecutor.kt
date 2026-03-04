package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.request.mapping.CronetRequestBuilderMapper
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

internal class DefaultCronetRequestExecutor(
    private val cronetEngine: CronetEngine,
    private val callbackExecutor: Executor,
) : CronetRequestExecutor {
    private val requestBuilderMapper: CronetRequestBuilderMapper = CronetRequestBuilderMapper(
        cronetEngine = cronetEngine,
        callbackExecutor = callbackExecutor,
    )

    override suspend fun execute(
        data: HttpRequestData,
        callContext: CoroutineContext,
    ): HttpResponseData {
        requestBuilderMapper.map(
            data = data,
            callback = PlaceholderCallback,
        )

        TODO(
            "Step 3: response bridge. " +
                "Use mapped UrlRequest.Builder to create/start request, " +
                "return HttpResponseData from onResponseStarted, then stream body via ByteReadChannel."
        )
    }

    private data object PlaceholderCallback : UrlRequest.Callback() {
        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) = Unit

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) = Unit

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) = Unit

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) = Unit

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) = Unit
    }
}
