package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.request.mapping.CronetRequestBuilderMapper
import com.connor.cronet.engine.internal.request.mapping.toKtorHeaders
import com.connor.cronet.engine.internal.request.mapping.toKtorProtocolVersion
import com.connor.cronet.engine.internal.request.mapping.toKtorStatusCode
import com.connor.cronet.engine.internal.request.pump.DirectByteBufferPool
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.request.ClientUpgradeContent
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.http.isWebsocket
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeFully
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

@OptIn(InternalAPI::class)
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
        val responseStreamProfile = if (data.getCapabilityOrNull(SSECapability) != null) {
            SSE_STREAM_PROFILE
        } else {
            DEFAULT_STREAM_PROFILE
        }
        val requestStartedAt = GMTDate()
        val responseDeferred = CompletableDeferred<HttpResponseData>(callContext[Job])
        val responseBodyChannel = ByteChannel(autoFlush = true)
        val bodyEvents = Channel<BodyEvent>(capacity = responseStreamProfile.bodyEventQueueCapacity)
        val callback = CronetUrlRequestCallback(
            requestData = data,
            callContext = callContext,
            requestStartedAt = requestStartedAt,
            responseDeferred = responseDeferred,
            responseBodyChannel = responseBodyChannel,
            bodyEvents = bodyEvents,
            responseStreamProfile = responseStreamProfile,
        )

        CoroutineScope(callContext + CoroutineName("cronet-body-channel-writer")).launch {
            try {
                for (event in bodyEvents) {
                    when (event) {
                        is BodyEvent.BytesChunk -> {
                            runCatching { responseBodyChannel.writeFully(event.bytes) }
                                .onFailure {
                                    responseBodyChannel.cancel(it)
                                    return@launch
                                }
                        }

                        BodyEvent.Completed -> {
                            responseBodyChannel.close()
                            return@launch
                        }
                    }
                }
            } finally {
                if (!responseBodyChannel.isClosedForWrite) {
                    responseBodyChannel.close()
                }
            }
        }

        val preparedRequest = try {
            requestBuilderMapper.map(
                data = data,
                callContext = callContext,
                callback = callback,
            )
        } catch (cause: Throwable) {
            bodyEvents.close()
            responseBodyChannel.cancel(cause)
            throw cause
        }

        val urlRequest = try {
            preparedRequest.requestBuilder.build()
        } catch (cause: Throwable) {
            bodyEvents.close()
            responseBodyChannel.cancel(cause)
            throw cause
        }
        val requestCancellation = RequestCancellationController(urlRequest)
        callback.bindRequestCanceler(requestCancellation::cancel)

        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                requestCancellation.cancel(cause)
            }
        }

        try {
            urlRequest.start()
        } catch (cause: Throwable) {
            bodyEvents.close()
            responseBodyChannel.cancel(cause)
            throw cause
        }
        callback.onRequestStarted()

        return try {
            responseDeferred.await()
        } catch (cause: Throwable) {
            requestCancellation.cancel(cause)
            throw cause
        }
    }

    private class CronetUrlRequestCallback(
        private val requestData: HttpRequestData,
        private val callContext: CoroutineContext,
        private val requestStartedAt: GMTDate,
        private val responseDeferred: CompletableDeferred<HttpResponseData>,
        private val responseBodyChannel: ByteChannel,
        private val bodyEvents: Channel<BodyEvent>,
        private val responseStreamProfile: ResponseStreamProfile,
    ) : UrlRequest.Callback() {
        private val terminal = AtomicBoolean(false)
        private val responseStarted = AtomicBoolean(false)
        private var readBuffer: ByteBuffer? = null
        private val timeoutScope = CoroutineScope(callContext + CoroutineName("cronet-timeout-controller"))
        private val timeoutConfig = requestData.getCapabilityOrNull(HttpTimeoutCapability)
        private val requestTimeoutMillis: Long? = if (requestData.supportsRequestTimeout()) {
            timeoutConfig.requestTimeoutOrNull()
        } else {
            null
        }
        private val connectTimeoutMillis: Long? = timeoutConfig.connectTimeoutOrNull()
        private val socketTimeoutMillis: Long? = timeoutConfig.socketTimeoutOrNull()
        private var requestTimeoutJob: Job? = null
        private var connectTimeoutJob: Job? = null
        private var socketTimeoutJob: Job? = null
        @Volatile
        private var requestCanceler: ((Throwable?) -> Unit)? = null

        fun bindRequestCanceler(canceler: (Throwable?) -> Unit) {
            requestCanceler = canceler
        }

        fun onRequestStarted() {
            startRequestTimeout()
            startConnectTimeout()
        }

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            if (terminal.get()) return
            request.followRedirect()
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (!responseStarted.compareAndSet(false, true)) return
            cancelConnectTimeout()
            resetSocketTimeout()

            val responseData = kotlin.runCatching {
                val status = info.toKtorStatusCode()
                val headers = info.toKtorHeaders()
                val version = info.toKtorProtocolVersion()

                val responseBody = requestData.attributes.getOrNull(ResponseAdapterAttributeKey)
                    ?.adapt(
                        data = requestData,
                        status = status,
                        headers = headers,
                        responseBody = responseBodyChannel,
                        outgoingContent = requestData.body,
                        callContext = callContext,
                    )
                    ?: responseBodyChannel

                HttpResponseData(
                    statusCode = status,
                    requestTime = requestStartedAt,
                    headers = headers,
                    version = version,
                    body = responseBody,
                    callContext = callContext,
                )
            }.getOrElse { cause ->
                finishFailure(cause)
                requestCancel(cause)
                return
            }

            if (!responseDeferred.complete(responseData)) {
                val cancellationCause = callCancellationOrDefault(
                    message = "Cronet response was canceled before response start completed",
                )
                finishFailure(cancellationCause)
                requestCancel(cancellationCause)
                return
            }

            val nextReadBuffer = responseStreamProfile.readBufferPool.acquire().also { readBuffer = it }
            request.read(nextReadBuffer)
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            if (terminal.get()) return

            byteBuffer.flip()
            if (byteBuffer.hasRemaining()) {
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)

                if (bodyEvents.trySend(BodyEvent.BytesChunk(bytes)).isFailure) {
                    val overflow = BodyQueueOverflowException(
                        streamProfileName = responseStreamProfile.name,
                        queueCapacity = responseStreamProfile.bodyEventQueueCapacity,
                    )
                    finishFailure(overflow)
                    requestCancel(overflow)
                    return
                }
            }
            byteBuffer.clear()
            resetSocketTimeout()

            if (!terminal.get()) {
                request.read(byteBuffer)
            }
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            finishSuccess()
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
            val mappedFailure = if (isCallCancelled()) {
                callCancellationOrDefault(
                    message = "Cronet request failed after call cancellation",
                    fallbackCause = error,
                )
            } else {
                error
            }
            finishFailure(mappedFailure)
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            finishFailure(
                callCancellationOrDefault(
                    message = "Cronet request was canceled",
                ),
            )
        }

        private fun finishSuccess() {
            if (!terminal.compareAndSet(false, true)) return

            cancelAllTimeouts()
            bodyEvents.trySend(BodyEvent.Completed)
            bodyEvents.close()
            releaseReadBuffer()
        }

        private fun finishFailure(cause: Throwable) {
            if (!terminal.compareAndSet(false, true)) return

            cancelAllTimeouts()
            if (!responseStarted.get()) {
                responseDeferred.completeExceptionally(cause)
            }

            responseBodyChannel.cancel(cause)
            bodyEvents.close()
            releaseReadBuffer()
        }

        private fun releaseReadBuffer() {
            val buffer = readBuffer ?: return
            readBuffer = null
            responseStreamProfile.readBufferPool.release(buffer)
        }

        private fun startRequestTimeout() {
            requestTimeoutJob?.cancel()
            val timeoutMillis = requestTimeoutMillis ?: return
            requestTimeoutJob = timeoutScope.launch {
                delay(timeoutMillis)
                if (!terminal.get()) {
                    val timeoutCause = HttpRequestTimeoutException(requestData)
                    finishFailure(timeoutCause)
                    requestCancel(timeoutCause)
                }
            }
        }

        private fun startConnectTimeout() {
            connectTimeoutJob?.cancel()
            val timeoutMillis = connectTimeoutMillis ?: return
            connectTimeoutJob = timeoutScope.launch {
                delay(timeoutMillis)
                if (!terminal.get() && !responseStarted.get()) {
                    val timeoutCause = ConnectTimeoutException(requestData)
                    finishFailure(timeoutCause)
                    requestCancel(timeoutCause)
                }
            }
        }

        private fun cancelConnectTimeout() {
            connectTimeoutJob?.cancel()
            connectTimeoutJob = null
        }

        private fun resetSocketTimeout() {
            socketTimeoutJob?.cancel()
            val timeoutMillis = socketTimeoutMillis ?: return
            socketTimeoutJob = timeoutScope.launch {
                delay(timeoutMillis)
                if (!terminal.get() && responseStarted.get()) {
                    val timeoutCause = SocketTimeoutException(requestData)
                    finishFailure(timeoutCause)
                    requestCancel(timeoutCause)
                }
            }
        }

        private fun cancelAllTimeouts() {
            requestTimeoutJob?.cancel()
            connectTimeoutJob?.cancel()
            socketTimeoutJob?.cancel()
            requestTimeoutJob = null
            connectTimeoutJob = null
            socketTimeoutJob = null
        }

        private fun requestCancel(cause: Throwable?) {
            requestCanceler?.invoke(cause)
        }

        private fun isCallCancelled(): Boolean {
            return callContext[Job]?.isCancelled == true
        }

        private fun callCancellationOrDefault(
            message: String,
            fallbackCause: Throwable? = null,
        ): CancellationException {
            val existingCancellation = fallbackCause as? CancellationException
            if (existingCancellation != null) {
                return existingCancellation
            }

            return CancellationException(message).apply {
                if (fallbackCause != null) {
                    initCause(fallbackCause)
                }
            }
        }
    }

    private sealed interface BodyEvent {
        data class BytesChunk(val bytes: ByteArray) : BodyEvent
        data object Completed : BodyEvent
    }

    private companion object {
        const val DEFAULT_READ_BUFFER_CAPACITY_BYTES: Int = 16 * 1024
        const val SSE_READ_BUFFER_CAPACITY_BYTES: Int = 4 * 1024

        const val DEFAULT_BODY_EVENT_QUEUE_CAPACITY: Int = 64
        const val SSE_BODY_EVENT_QUEUE_CAPACITY: Int = 256

        val DEFAULT_READ_BUFFER_POOL: DirectByteBufferPool = DirectByteBufferPool(
            bufferSizeBytes = DEFAULT_READ_BUFFER_CAPACITY_BYTES,
            maxPooledBuffers = 128,
        )

        val SSE_READ_BUFFER_POOL: DirectByteBufferPool = DirectByteBufferPool(
            bufferSizeBytes = SSE_READ_BUFFER_CAPACITY_BYTES,
            maxPooledBuffers = 256,
        )

        val DEFAULT_STREAM_PROFILE: ResponseStreamProfile = ResponseStreamProfile(
            name = "default-http-response",
            bodyEventQueueCapacity = DEFAULT_BODY_EVENT_QUEUE_CAPACITY,
            readBufferPool = DEFAULT_READ_BUFFER_POOL,
        )

        val SSE_STREAM_PROFILE: ResponseStreamProfile = ResponseStreamProfile(
            name = "sse-response-stream",
            bodyEventQueueCapacity = SSE_BODY_EVENT_QUEUE_CAPACITY,
            readBufferPool = SSE_READ_BUFFER_POOL,
        )
    }

    private data class ResponseStreamProfile(
        val name: String,
        val bodyEventQueueCapacity: Int,
        val readBufferPool: DirectByteBufferPool,
    )

    private class BodyQueueOverflowException(
        streamProfileName: String,
        queueCapacity: Int,
    ) : IllegalStateException(
        "Cronet response body queue overflowed while bridging callback thread to channel writer " +
            "[profile=$streamProfileName, queue_capacity=$queueCapacity]",
    )

    private class RequestCancellationController(
        private val request: UrlRequest,
    ) {
        private val cancelRequested = AtomicBoolean(false)

        fun cancel(cause: Throwable?) {
            if (!cancelRequested.compareAndSet(false, true)) {
                return
            }
            runCatching { request.cancel() }
        }
    }
}

private fun HttpTimeoutConfig?.requestTimeoutOrNull(): Long? {
    return this?.requestTimeoutMillis.toFiniteTimeoutOrNull()
}

private fun HttpTimeoutConfig?.connectTimeoutOrNull(): Long? {
    return this?.connectTimeoutMillis.toFiniteTimeoutOrNull()
}

private fun HttpTimeoutConfig?.socketTimeoutOrNull(): Long? {
    return this?.socketTimeoutMillis.toFiniteTimeoutOrNull()
}

private fun Long?.toFiniteTimeoutOrNull(): Long? {
    return when (this) {
        null,
        HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
        -> null

        else -> this
    }
}

@OptIn(InternalAPI::class)
private fun HttpRequestData.supportsRequestTimeout(): Boolean {
    return !url.protocol.isWebsocket() &&
        body !is ClientUpgradeContent &&
        getCapabilityOrNull(SSECapability) == null
}
