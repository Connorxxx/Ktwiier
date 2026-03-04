package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.request.mapping.CronetRequestBuilderMapper
import com.connor.cronet.engine.internal.request.mapping.toKtorHeaders
import com.connor.cronet.engine.internal.request.mapping.toKtorProtocolVersion
import com.connor.cronet.engine.internal.request.mapping.toKtorStatusCode
import com.connor.cronet.engine.internal.request.pump.DirectByteBufferPool
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
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
        val requestStartedAt = GMTDate()
        val responseDeferred = CompletableDeferred<HttpResponseData>(callContext[Job])
        val responseBodyChannel = ByteChannel(autoFlush = true)
        val bodyEvents = Channel<BodyEvent>(capacity = BODY_EVENT_QUEUE_CAPACITY)
        val callback = CronetUrlRequestCallback(
            requestData = data,
            callContext = callContext,
            requestStartedAt = requestStartedAt,
            responseDeferred = responseDeferred,
            responseBodyChannel = responseBodyChannel,
            bodyEvents = bodyEvents,
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

        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                urlRequest.cancel()
            }
        }

        try {
            urlRequest.start()
        } catch (cause: Throwable) {
            bodyEvents.close()
            responseBodyChannel.cancel(cause)
            throw cause
        }

        return try {
            responseDeferred.await()
        } catch (cause: Throwable) {
            urlRequest.cancel()
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
    ) : UrlRequest.Callback() {
        private val terminal = AtomicBoolean(false)
        private val responseStarted = AtomicBoolean(false)
        private var readBuffer: ByteBuffer? = null

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
            if (terminal.get()) return
            request.followRedirect()
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (!responseStarted.compareAndSet(false, true)) return

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
                request.cancel()
                return
            }

            if (!responseDeferred.complete(responseData)) {
                finishFailure(CancellationException("Cronet response was canceled before response start completed"))
                request.cancel()
                return
            }

            val nextReadBuffer = READ_BUFFER_POOL.acquire().also { readBuffer = it }
            request.read(nextReadBuffer)
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            if (terminal.get()) return

            byteBuffer.flip()
            if (byteBuffer.hasRemaining()) {
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)

                if (bodyEvents.trySend(BodyEvent.BytesChunk(bytes)).isFailure) {
                    finishFailure(
                        BodyQueueOverflowException(
                            "Cronet response body queue overflowed while bridging callback thread to channel writer",
                        ),
                    )
                    request.cancel()
                    return
                }
            }
            byteBuffer.clear()

            if (!terminal.get()) {
                request.read(byteBuffer)
            }
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            finishSuccess()
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
            finishFailure(error)
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            finishFailure(CancellationException("Cronet request was canceled"))
        }

        private fun finishSuccess() {
            if (!terminal.compareAndSet(false, true)) return

            bodyEvents.trySend(BodyEvent.Completed)
            bodyEvents.close()
            releaseReadBuffer()
        }

        private fun finishFailure(cause: Throwable) {
            if (!terminal.compareAndSet(false, true)) return

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
            READ_BUFFER_POOL.release(buffer)
        }
    }

    private sealed interface BodyEvent {
        data class BytesChunk(val bytes: ByteArray) : BodyEvent
        data object Completed : BodyEvent
    }

    private companion object {
        const val READ_BUFFER_CAPACITY_BYTES: Int = 16 * 1024
        const val BODY_EVENT_QUEUE_CAPACITY: Int = 64

        val READ_BUFFER_POOL: DirectByteBufferPool = DirectByteBufferPool(
            bufferSizeBytes = READ_BUFFER_CAPACITY_BYTES,
            maxPooledBuffers = 128,
        )
    }

    private class BodyQueueOverflowException(message: String) : IllegalStateException(message)
}
