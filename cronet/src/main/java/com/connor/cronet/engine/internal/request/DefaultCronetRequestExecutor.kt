package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.fault.CronetFaultInjector
import com.connor.cronet.engine.internal.fault.CronetInvariantRecorder
import com.connor.cronet.engine.internal.fault.CronetRequestFaultContext
import com.connor.cronet.engine.internal.fault.CronetRequestPhase
import com.connor.cronet.engine.internal.fault.NoopCronetFaultInjector
import com.connor.cronet.engine.internal.fault.NoopCronetInvariantRecorder
import com.connor.cronet.engine.internal.request.mapping.CronetRequestBuilderMapper
import com.connor.cronet.engine.internal.request.mapping.toKtorHeaders
import com.connor.cronet.engine.internal.request.mapping.toKtorProtocolVersion
import com.connor.cronet.engine.internal.request.mapping.toKtorStatusCode
import com.connor.cronet.engine.internal.request.pump.DirectByteBufferPool
import com.connor.cronet.engine.internal.telemetry.CronetExceptionClassification
import com.connor.cronet.engine.internal.telemetry.CronetRequestCompletionReason
import com.connor.cronet.engine.internal.telemetry.CronetRequestFailure
import com.connor.cronet.engine.internal.telemetry.CronetRequestTelemetryEvent
import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import com.connor.cronet.engine.internal.telemetry.TimeoutKind
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.chromium.net.CallbackException
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.NetworkException
import org.chromium.net.QuicException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

@OptIn(InternalAPI::class)
internal class DefaultCronetRequestExecutor(
    private val cronetEngine: CronetEngine,
    private val callbackExecutor: Executor,
    private val telemetry: CronetTelemetry,
    private val faultInjector: CronetFaultInjector = NoopCronetFaultInjector,
    private val invariantRecorder: CronetInvariantRecorder = NoopCronetInvariantRecorder,
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
        val requestStartNanos = System.nanoTime()
        val requestKey = REQUEST_KEY_SEQUENCE.incrementAndGet()
        val requestStartedAt = GMTDate()
        val responseDeferred = CompletableDeferred<HttpResponseData>(callContext[Job])
        val responseBodyChannel = ByteChannel(autoFlush = true)
        val callback = CronetUrlRequestCallback(
            requestKey = requestKey,
            requestData = data,
            callContext = callContext,
            requestStartedAt = requestStartedAt,
            requestStartNanos = requestStartNanos,
            responseDeferred = responseDeferred,
            responseBodyChannel = responseBodyChannel,
            responseStreamProfile = responseStreamProfile,
            telemetry = telemetry,
            faultInjector = faultInjector,
            invariantRecorder = invariantRecorder,
            requestMethodExecutor = callbackExecutor,
        )

        fun failRequestBeforeCallbackExecution(cause: Throwable): Nothing {
            responseBodyChannel.cancel(cause)
            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = data,
                requestStartNanos = requestStartNanos,
                completionReason = CronetRequestCompletionReason.Failed,
                responseInfo = null,
                cause = cause,
            )
            recordTerminalInvariant(
                invariantRecorder = invariantRecorder,
                requestKey = requestKey,
                completionReason = CronetRequestCompletionReason.Failed,
                duplicate = false,
            )
            throw cause
        }

        val preparedRequest = try {
            requestBuilderMapper.map(
                data = data,
                callContext = callContext,
                callback = callback,
            )
        } catch (cause: Throwable) {
            failRequestBeforeCallbackExecution(cause)
        }

        val urlRequest = try {
            preparedRequest.requestBuilder.build()
        } catch (cause: Throwable) {
            failRequestBeforeCallbackExecution(cause)
        }
        val requestCancellation = RequestCancellationController(
            request = urlRequest,
            requestMethodExecutor = callbackExecutor,
        )
        callback.bindRequestCanceler(requestCancellation::cancel)

        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                requestCancellation.cancel(cause)
            }
        }

        callback.onBeforeRequestStart()?.let { cause ->
            failRequestBeforeCallbackExecution(cause)
        }
        if (callContext[Job]?.isCancelled == true) {
            failRequestBeforeCallbackExecution(
                CancellationException("Cronet request call context was canceled before start"),
            )
        }

        try {
            executeOnRequestMethodExecutor(callbackExecutor) {
                urlRequest.start()
            }
        } catch (cause: Throwable) {
            failRequestBeforeCallbackExecution(cause)
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
        private val requestKey: Long,
        private val requestData: HttpRequestData,
        private val callContext: CoroutineContext,
        private val requestStartedAt: GMTDate,
        private val requestStartNanos: Long,
        private val responseDeferred: CompletableDeferred<HttpResponseData>,
        private val responseBodyChannel: ByteChannel,
        private val responseStreamProfile: ResponseStreamProfile,
        private val telemetry: CronetTelemetry,
        private val faultInjector: CronetFaultInjector,
        private val invariantRecorder: CronetInvariantRecorder,
        private val requestMethodExecutor: Executor,
    ) : UrlRequest.Callback() {
        private val terminal = SingleTerminalLatch()
        private val responseStarted = AtomicBoolean(false)
        private var readBuffer: ByteBuffer? = null
        @Volatile
        private var latestResponseInfo: UrlResponseInfo? = null
        private val timeoutScope = CoroutineScope(callContext + CoroutineName("cronet-timeout-controller"))
        private val bodyWriteScope = CoroutineScope(callContext + CoroutineName("cronet-body-write-bridge"))
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

        fun onBeforeRequestStart(): Throwable? {
            return injectFault(phase = CronetRequestPhase.BeforeStart)
        }

        fun onRequestStarted() {
            startRequestTimeout()
            startConnectTimeout()
        }

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, _newLocationUrl: String) {
            if (terminal.isTerminal || !responseStarted.compareAndSet(false, true)) return
            latestResponseInfo = info

            // Surface 3xx to Ktor's HttpRedirect plugin instead of following in-engine.
            cancelConnectTimeout()
            val responseData = kotlin.runCatching {
                buildHttpResponseData(info)
            }.getOrElse { cause ->
                finishFailure(cause, info)
                requestCancel(cause)
                return
            }

            if (!responseDeferred.complete(responseData)) {
                val cancellationCause = callCancellationOrDefault(
                    message = "Cronet redirect response was canceled before completion",
                )
                finishFailure(cancellationCause, info)
                requestCancel(cancellationCause)
                return
            }

            finishSuccess(info)
            requestCancel(
                CancellationException(
                    "Cronet redirect response handed off to Ktor redirect pipeline",
                ),
            )
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (terminal.isTerminal || !responseStarted.compareAndSet(false, true)) return
            latestResponseInfo = info

            val injectedFailure = injectFault(
                phase = CronetRequestPhase.ResponseStarted,
            )
            if (injectedFailure != null) {
                finishFailure(injectedFailure, info)
                requestCancel(injectedFailure)
                return
            }

            cancelConnectTimeout()
            resetSocketTimeout()

            val responseData = kotlin.runCatching {
                buildHttpResponseData(info)
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
            if (terminal.isTerminal) return

            byteBuffer.flip()
            val chunkSizeBytes = byteBuffer.remaining().takeIf { it > 0 }

            val injectedFailure = injectFault(
                phase = CronetRequestPhase.ReadCompleted,
                bodyChunkSizeBytes = chunkSizeBytes,
            )
            if (injectedFailure != null) {
                finishFailure(injectedFailure, info)
                requestCancel(injectedFailure)
                return
            }

            if (!byteBuffer.hasRemaining()) {
                byteBuffer.clear()
                resetSocketTimeout()

                if (!terminal.isTerminal) {
                    request.read(byteBuffer)
                }
                return
            }

            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            byteBuffer.clear()
            resetSocketTimeout()

            bodyWriteScope.launch {
                runCatching { responseBodyChannel.writeFully(bytes) }
                    .onFailure { cause ->
                        finishFailure(cause, info)
                        requestCancel(cause)
                        return@launch
                    }

                if (terminal.isTerminal) {
                    return@launch
                }

                dispatchOnRequestMethodExecutor(
                    requestMethodExecutor = requestMethodExecutor,
                    onFailure = { cause ->
                        finishFailure(cause, info)
                        requestCancel(cause)
                    },
                ) {
                    if (!terminal.isTerminal) {
                        request.read(byteBuffer)
                    }
                }
            }
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            finishSuccess(info)
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
            finishFailure(mappedFailure, info)
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            finishFailure(
                callCancellationOrDefault(
                    message = "Cronet request was canceled",
                ),
                info,
            )
        }

        private fun finishSuccess(responseInfo: UrlResponseInfo) {
            if (!terminal.tryEnterTerminal()) {
                recordTerminalInvariant(
                    invariantRecorder = invariantRecorder,
                    requestKey = requestKey,
                    completionReason = CronetRequestCompletionReason.Succeeded,
                    duplicate = true,
                )
                return
            }

            recordTerminalInvariant(
                invariantRecorder = invariantRecorder,
                requestKey = requestKey,
                completionReason = CronetRequestCompletionReason.Succeeded,
                duplicate = false,
            )
            injectFault(
                phase = CronetRequestPhase.TerminalSucceeded,
            )

            latestResponseInfo = responseInfo
            cancelAllTimeouts()
            if (!responseBodyChannel.isClosedForWrite) {
                responseBodyChannel.close()
            }
            releaseReadBuffer()
            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = requestData,
                requestStartNanos = requestStartNanos,
                completionReason = CronetRequestCompletionReason.Succeeded,
                responseInfo = responseInfo,
                cause = null,
            )
        }

        private fun finishFailure(cause: Throwable, responseInfo: UrlResponseInfo? = null) {
            val completionReason = if (cause is CancellationException) {
                CronetRequestCompletionReason.Canceled
            } else {
                CronetRequestCompletionReason.Failed
            }

            if (!terminal.tryEnterTerminal()) {
                recordTerminalInvariant(
                    invariantRecorder = invariantRecorder,
                    requestKey = requestKey,
                    completionReason = completionReason,
                    duplicate = true,
                )
                return
            }

            recordTerminalInvariant(
                invariantRecorder = invariantRecorder,
                requestKey = requestKey,
                completionReason = completionReason,
                duplicate = false,
            )
            injectFault(
                phase = if (cause is CancellationException) {
                    CronetRequestPhase.TerminalCanceled
                } else {
                    CronetRequestPhase.TerminalFailed
                },
                cause = cause,
            )

            val finalResponseInfo = responseInfo ?: latestResponseInfo
            latestResponseInfo = finalResponseInfo
            cancelAllTimeouts()
            responseDeferred.completeExceptionally(cause)

            responseBodyChannel.cancel(cause)
            releaseReadBuffer()
            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = requestData,
                requestStartNanos = requestStartNanos,
                completionReason = completionReason,
                responseInfo = finalResponseInfo,
                cause = cause,
            )
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
                if (!terminal.isTerminal) {
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
                if (!terminal.isTerminal && !responseStarted.get()) {
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
                if (!terminal.isTerminal && responseStarted.get()) {
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

        private fun buildHttpResponseData(info: UrlResponseInfo): HttpResponseData {
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

            return HttpResponseData(
                statusCode = status,
                requestTime = requestStartedAt,
                headers = headers,
                version = version,
                body = responseBody,
                callContext = callContext,
            )
        }

        private fun injectFault(
            phase: CronetRequestPhase,
            bodyChunkSizeBytes: Int? = null,
            cause: Throwable? = null,
        ): Throwable? {
            return runCatching {
                faultInjector.onRequestPhase(
                    CronetRequestFaultContext(
                        requestKey = requestKey,
                        method = requestData.method.value,
                        url = requestData.url.toString(),
                        phase = phase,
                        bodyChunkSizeBytes = bodyChunkSizeBytes,
                        cause = cause,
                    ),
                )
            }.exceptionOrNull()
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

    private companion object {
        val REQUEST_KEY_SEQUENCE: AtomicLong = AtomicLong(0L)

        const val DEFAULT_READ_BUFFER_CAPACITY_BYTES: Int = 16 * 1024
        const val SSE_READ_BUFFER_CAPACITY_BYTES: Int = 4 * 1024

        val DEFAULT_READ_BUFFER_POOL: DirectByteBufferPool = DirectByteBufferPool(
            bufferSizeBytes = DEFAULT_READ_BUFFER_CAPACITY_BYTES,
            maxPooledBuffers = 128,
        )

        val SSE_READ_BUFFER_POOL: DirectByteBufferPool = DirectByteBufferPool(
            bufferSizeBytes = SSE_READ_BUFFER_CAPACITY_BYTES,
            maxPooledBuffers = 256,
        )

        val DEFAULT_STREAM_PROFILE: ResponseStreamProfile = ResponseStreamProfile(
            readBufferPool = DEFAULT_READ_BUFFER_POOL,
        )

        val SSE_STREAM_PROFILE: ResponseStreamProfile = ResponseStreamProfile(
            readBufferPool = SSE_READ_BUFFER_POOL,
        )
    }

    private data class ResponseStreamProfile(
        val readBufferPool: DirectByteBufferPool,
    )

    private class RequestCancellationController(
        private val request: UrlRequest,
        private val requestMethodExecutor: Executor,
    ) {
        private val cancelRequested = AtomicBoolean(false)

        fun cancel(cause: Throwable?) {
            if (!cancelRequested.compareAndSet(false, true)) {
                return
            }
            dispatchOnRequestMethodExecutor(
                requestMethodExecutor = requestMethodExecutor,
                onFailure = { },
            ) {
                request.cancel()
            }
        }
    }
}

private suspend fun executeOnRequestMethodExecutor(
    requestMethodExecutor: Executor,
    block: () -> Unit,
) {
    val completion = CompletableDeferred<Unit>()
    dispatchOnRequestMethodExecutor(
        requestMethodExecutor = requestMethodExecutor,
        onFailure = { cause -> completion.completeExceptionally(cause) },
    ) {
        block()
        completion.complete(Unit)
    }
    completion.await()
}

private fun dispatchOnRequestMethodExecutor(
    requestMethodExecutor: Executor,
    onFailure: (Throwable) -> Unit,
    block: () -> Unit,
) {
    try {
        requestMethodExecutor.execute {
            runCatching(block).onFailure(onFailure)
        }
    } catch (cause: Throwable) {
        onFailure(cause)
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

private fun recordTerminalInvariant(
    invariantRecorder: CronetInvariantRecorder,
    requestKey: Long,
    completionReason: CronetRequestCompletionReason,
    duplicate: Boolean,
) {
    runCatching {
        invariantRecorder.onTerminalEvent(
            requestKey = requestKey,
            completionReason = completionReason,
            duplicate = duplicate,
        )
    }
}

private fun emitRequestTelemetry(
    telemetry: CronetTelemetry,
    requestData: HttpRequestData,
    requestStartNanos: Long,
    completionReason: CronetRequestCompletionReason,
    responseInfo: UrlResponseInfo?,
    cause: Throwable?,
) {
    val durationMillis = ((System.nanoTime() - requestStartNanos).coerceAtLeast(0L)) / 1_000_000L
    val negotiatedProtocol = responseInfo
        ?.negotiatedProtocol
        ?.trim()
        ?.ifEmpty { null }

    val event = CronetRequestTelemetryEvent(
        method = requestData.method.value,
        url = requestData.url.toString(),
        durationMillis = durationMillis,
        statusCode = responseInfo?.httpStatusCode,
        negotiatedProtocol = negotiatedProtocol,
        completionReason = completionReason,
        failure = cause?.toRequestFailure(),
    )

    runCatching { telemetry.onRequestFinished(event) }
}

private fun Throwable.toRequestFailure(): CronetRequestFailure {
    return when (this) {
        is HttpRequestTimeoutException -> CronetRequestFailure.Timeout(TimeoutKind.Request)
        is ConnectTimeoutException -> CronetRequestFailure.Timeout(TimeoutKind.Connect)
        is SocketTimeoutException -> CronetRequestFailure.Timeout(TimeoutKind.Socket)
        is CancellationException -> CronetRequestFailure.Cancellation(message)
        is CronetException -> CronetRequestFailure.Cronet(toCronetExceptionClassification())
        else -> CronetRequestFailure.Other(
            throwableClass = this::class.qualifiedName ?: this.javaClass.name,
            message = message,
        )
    }
}

private fun CronetException.toCronetExceptionClassification(): CronetExceptionClassification {
    return when (this) {
        is QuicException -> CronetExceptionClassification.Quic(
            errorCode = getErrorCode(),
            internalErrorCode = getCronetInternalErrorCode(),
            immediatelyRetryable = immediatelyRetryable(),
            quicDetailedErrorCode = getQuicDetailedErrorCode(),
            connectionCloseSource = getConnectionCloseSource(),
        )

        is NetworkException -> CronetExceptionClassification.Network(
            errorCode = getErrorCode(),
            internalErrorCode = getCronetInternalErrorCode(),
            immediatelyRetryable = immediatelyRetryable(),
        )

        is CallbackException -> CronetExceptionClassification.Callback(
            callbackCauseClass = cause?.javaClass?.name,
            callbackCauseMessage = cause?.message,
        )

        else -> CronetExceptionClassification.Other(
            throwableClass = this::class.qualifiedName ?: this.javaClass.name,
            message = message,
        )
    }
}

@OptIn(InternalAPI::class)
private fun HttpRequestData.supportsRequestTimeout(): Boolean {
    return !url.protocol.isWebsocket() &&
        body !is ClientUpgradeContent &&
        getCapabilityOrNull(SSECapability) == null
}
