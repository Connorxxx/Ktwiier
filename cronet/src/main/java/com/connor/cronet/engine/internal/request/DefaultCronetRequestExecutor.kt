package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.CronetResponseStreamProfile
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
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    defaultResponseStreamProfile: CronetResponseStreamProfile,
    sseResponseStreamProfile: CronetResponseStreamProfile,
    private val telemetry: CronetTelemetry,
    private val faultInjector: CronetFaultInjector = NoopCronetFaultInjector,
    private val invariantRecorder: CronetInvariantRecorder = NoopCronetInvariantRecorder,
) : CronetRequestExecutor {
    private val requestBuilderMapper: CronetRequestBuilderMapper = CronetRequestBuilderMapper(
        cronetEngine = cronetEngine,
        callbackExecutor = callbackExecutor,
    )

    private val defaultStreamProfile: ResponseStreamProfile = ResponseStreamProfile.fromConfig(defaultResponseStreamProfile)
    private val sseStreamProfile: ResponseStreamProfile = ResponseStreamProfile.fromConfig(sseResponseStreamProfile)

    override suspend fun execute(
        data: HttpRequestData,
        callContext: CoroutineContext,
        lifecycleHandle: CronetRequestLifecycleHandle,
    ): HttpResponseData {
        val responseStreamProfile = if (data.getCapabilityOrNull(SSECapability) != null) {
            sseStreamProfile
        } else {
            defaultStreamProfile
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
            lifecycleHandle = lifecycleHandle,
            telemetry = telemetry,
            faultInjector = faultInjector,
            invariantRecorder = invariantRecorder,
            requestMethodExecutor = callbackExecutor,
        )

        fun failRequestBeforeCallbackExecution(cause: Throwable): Nothing {
            callback.dispose(cause)
            lifecycleHandle.markTerminal()
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
        lifecycleHandle.bindTransportCanceler(requestCancellation::cancel)

        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                callback.onCallContextCancelled(cause)
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
        private val lifecycleHandle: CronetRequestLifecycleHandle,
        private val telemetry: CronetTelemetry,
        private val faultInjector: CronetFaultInjector,
        private val invariantRecorder: CronetInvariantRecorder,
        private val requestMethodExecutor: Executor,
    ) : UrlRequest.Callback() {
        private val terminal = SingleTerminalLatch()
        private val responseStarted = AtomicBoolean(false)
        @Volatile
        private var latestResponseInfo: UrlResponseInfo? = null

        /**
         * Keep bridge scopes independent from callContext Job.
         *
         * Ktor SSE acquires a session using HttpStatement.body { ... } and then runs response cleanup,
         * which can complete callContext while the SSE stream still needs incremental reads.
         */
        private val callbackBridgeContext = callContext.minusKey(Job)
        private val timeoutScope = CoroutineScope(
            callbackBridgeContext + SupervisorJob() + CoroutineName("cronet-timeout-controller"),
        )
        private val terminalScope = CoroutineScope(
            callbackBridgeContext + SupervisorJob() + CoroutineName("cronet-terminal-cleanup"),
        )
        private val bodyWriteScopeJob = SupervisorJob()
        private val bodyWriteScope = CoroutineScope(
            callbackBridgeContext + bodyWriteScopeJob + CoroutineName("cronet-body-write-bridge"),
        )

        private val writeMutex = Mutex()
        private val readCreditRing = ReadCreditRing(responseStreamProfile)
        private val readSchedulingLock = Any()
        private var awaitingReadCredit: Boolean = false
        private var activeReadBuffer: ByteBuffer? = null
        private val buffersReleased = AtomicBoolean(false)
        private val successFinalized = AtomicBoolean(false)

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
        private val timeoutFailureCause = AtomicReference<Throwable?>(null)

        @Volatile
        private var requestCanceler: ((Throwable?) -> Unit)? = null

        init {
            bodyWriteScopeJob.invokeOnCompletion {
                releaseReadBuffers()
            }
        }

        fun bindRequestCanceler(canceler: (Throwable?) -> Unit) {
            requestCanceler = canceler
        }

        fun onCallContextCancelled(cause: Throwable) {
            cancelBridgeScopes(cause)
        }

        fun onBeforeRequestStart(): Throwable? {
            return injectFault(phase = CronetRequestPhase.BeforeStart)
        }

        fun onRequestStarted() {
            startRequestTimeout()
            startConnectTimeout()
        }

        fun dispose(cause: Throwable?) {
            cancelAllTimeouts()
            cancelBridgeScopes(cause)
            terminalScope.cancel(
                cause as? CancellationException ?: CancellationException("Cronet request terminal scope canceled"),
            )
            releaseReadBuffers()
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

            val nextReadBuffer = readCreditRing.acquireForRead()
            if (nextReadBuffer == null) {
                val cause = IllegalStateException(
                    "Cronet read credit ring could not provide initial read buffer",
                )
                finishFailure(cause, info)
                requestCancel(cause)
                return
            }
            dispatchRead(request = request, info = info, buffer = nextReadBuffer)
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            if (terminal.isTerminal) return
            clearActiveReadBuffer(byteBuffer)

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
                readCreditRing.recycle(byteBuffer)
                resetSocketTimeout()
                scheduleReadOrAwaitCredit(request = request, info = info)
                return
            }

            resetSocketTimeout()

            bodyWriteScope.launch {
                runCatching {
                    writeMutex.withLock {
                        responseBodyChannel.writeFully(byteBuffer)
                    }
                }.onFailure { cause ->
                    byteBuffer.clear()
                    readCreditRing.recycle(byteBuffer)
                    finishFailure(cause, info)
                    requestCancel(cause)
                    return@launch
                }

                byteBuffer.clear()
                readCreditRing.recycle(byteBuffer)

                if (terminal.isTerminal) {
                    return@launch
                }

                scheduleReadIfAwaitingCredit(request = request, info = info)
            }

            scheduleReadOrAwaitCredit(request = request, info = info)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            finishSuccess(info)
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
            val timeoutCause = timeoutFailureCause.getAndSet(null)
            val mappedFailure = if (timeoutCause != null) {
                timeoutCause
            } else if (isCallCancelled()) {
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
            val timeoutCause = timeoutFailureCause.getAndSet(null)
            finishFailure(
                timeoutCause ?: callCancellationOrDefault(
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
            timeoutFailureCause.set(null)
            clearActiveReadBuffer(null)?.let(readCreditRing::recycle)
            lifecycleHandle.markTerminal()
            cancelAllTimeouts()

            terminalScope.launch {
                runCatching {
                    writeMutex.withLock { Unit }
                }.onFailure { cause ->
                    finishFailure(cause, responseInfo)
                    requestCancel(cause)
                    return@launch
                }

                finalizeSuccess(responseInfo)
            }
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
            timeoutFailureCause.set(null)
            clearActiveReadBuffer(null)?.let(readCreditRing::recycle)
            lifecycleHandle.markTerminal()
            cancelAllTimeouts()
            cancelBridgeScopes(cause)
            terminalScope.cancel(
                cause as? CancellationException ?: CancellationException(
                    "Cronet request terminal scope canceled",
                ).apply {
                    initCause(cause)
                },
            )
            responseDeferred.completeExceptionally(cause)

            responseBodyChannel.cancel(cause)
            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = requestData,
                requestStartNanos = requestStartNanos,
                completionReason = completionReason,
                responseInfo = finalResponseInfo,
                cause = cause,
            )
        }

        private fun finalizeSuccess(responseInfo: UrlResponseInfo) {
            if (!successFinalized.compareAndSet(false, true)) {
                return
            }

            if (!responseBodyChannel.isClosedForWrite) {
                responseBodyChannel.close()
            }
            cancelBridgeScopes()
            terminalScope.cancel()
            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = requestData,
                requestStartNanos = requestStartNanos,
                completionReason = CronetRequestCompletionReason.Succeeded,
                responseInfo = responseInfo,
                cause = null,
            )
        }

        private fun releaseReadBuffers() {
            if (!buffersReleased.compareAndSet(false, true)) {
                return
            }
            readCreditRing.releaseAll()
        }

        private fun scheduleReadOrAwaitCredit(request: UrlRequest, info: UrlResponseInfo) {
            val nextBuffer = synchronized(readSchedulingLock) {
                if (terminal.isTerminal) {
                    null
                } else {
                    val acquired = readCreditRing.acquireForRead()
                    if (acquired != null) {
                        awaitingReadCredit = false
                        acquired
                    } else {
                        awaitingReadCredit = true
                        runCatching {
                            invariantRecorder.onBodyQueueOverflow(
                                requestKey = requestKey,
                                queueCapacity = responseStreamProfile.maxInFlightBuffers,
                            )
                        }
                        null
                    }
                }
            } ?: return

            dispatchRead(request = request, info = info, buffer = nextBuffer)
        }

        private fun scheduleReadIfAwaitingCredit(request: UrlRequest, info: UrlResponseInfo) {
            val nextBuffer = synchronized(readSchedulingLock) {
                if (!awaitingReadCredit || terminal.isTerminal) {
                    null
                } else {
                    val acquired = readCreditRing.acquireForRead()
                    if (acquired != null) {
                        awaitingReadCredit = false
                    }
                    acquired
                }
            } ?: return

            dispatchRead(request = request, info = info, buffer = nextBuffer)
        }

        private fun dispatchRead(
            request: UrlRequest,
            info: UrlResponseInfo,
            buffer: ByteBuffer,
        ) {
            dispatchOnRequestMethodExecutor(
                requestMethodExecutor = requestMethodExecutor,
                onFailure = { cause ->
                    readCreditRing.recycle(buffer)
                    clearActiveReadBuffer(buffer)
                    finishFailure(cause, info)
                    requestCancel(cause)
                },
            ) {
                synchronized(readSchedulingLock) {
                    if (terminal.isTerminal) {
                        readCreditRing.recycle(buffer)
                        return@dispatchOnRequestMethodExecutor
                    }
                    activeReadBuffer = buffer
                }
                request.read(buffer)
            }
        }

        private fun clearActiveReadBuffer(buffer: ByteBuffer?): ByteBuffer? {
            return synchronized(readSchedulingLock) {
                val current = activeReadBuffer
                if (buffer == null || current === buffer) {
                    activeReadBuffer = null
                    awaitingReadCredit = false
                    current
                } else {
                    null
                }
            }
        }

        private fun startRequestTimeout() {
            requestTimeoutJob?.cancel()
            val timeoutMillis = requestTimeoutMillis ?: return
            requestTimeoutJob = timeoutScope.launch {
                delay(timeoutMillis)
                if (!terminal.isTerminal) {
                    val timeoutCause = HttpRequestTimeoutException(requestData)
                    timeoutFailureCause.compareAndSet(null, timeoutCause)
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
                    timeoutFailureCause.compareAndSet(null, timeoutCause)
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
                    timeoutFailureCause.compareAndSet(null, timeoutCause)
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

        private fun cancelBridgeScopes(cause: Throwable? = null) {
            val cancellationCause = cause as? CancellationException ?: CancellationException(
                "Cronet request bridge scopes canceled",
            ).apply {
                if (cause != null) {
                    initCause(cause)
                }
            }
            timeoutScope.cancel(cancellationCause)
            bodyWriteScope.cancel(cancellationCause)
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
    }

    private data class ResponseStreamProfile(
        val readBufferPool: DirectByteBufferPool,
        val maxInFlightBuffers: Int,
    ) {
        companion object {
            fun fromConfig(config: CronetResponseStreamProfile): ResponseStreamProfile {
                return ResponseStreamProfile(
                    readBufferPool = DirectByteBufferPool(
                        bufferSizeBytes = config.readBufferSizeBytes,
                        maxPooledBuffers = config.maxPooledBuffers,
                    ),
                    maxInFlightBuffers = config.maxInFlightBuffers,
                )
            }
        }
    }

    private class ReadCreditRing(
        private val profile: ResponseStreamProfile,
    ) {
        private val capacity = profile.maxInFlightBuffers
        private val buffers: Array<ByteBuffer> = Array(capacity) {
            profile.readBufferPool.acquire()
        }
        private val slotStates: AtomicIntegerArray = AtomicIntegerArray(capacity)
        private val acquireCursor: AtomicInteger = AtomicInteger(0)
        private val released = AtomicBoolean(false)
        private val bufferSlotIndex = IdentityHashMap<ByteBuffer, Int>(capacity)

        init {
            buffers.forEachIndexed { index, buffer ->
                bufferSlotIndex[buffer] = index
            }
        }

        fun acquireForRead(): ByteBuffer? {
            if (released.get()) {
                return null
            }

            val start = acquireCursor.getAndIncrement()
            for (offset in 0 until capacity) {
                val slot = floorMod(start = start, offset = offset, size = capacity)
                if (!slotStates.compareAndSet(slot, SLOT_AVAILABLE, SLOT_IN_FLIGHT)) {
                    continue
                }
                return buffers[slot].apply { clear() }
            }

            return null
        }

        fun recycle(buffer: ByteBuffer) {
            if (released.get()) {
                return
            }

            val slot = bufferSlotIndex[buffer] ?: return
            if (slotStates.compareAndSet(slot, SLOT_IN_FLIGHT, SLOT_AVAILABLE)) {
                buffer.clear()
            }
        }

        fun releaseAll() {
            if (!released.compareAndSet(false, true)) {
                return
            }

            for (slot in 0 until capacity) {
                while (true) {
                    val state = slotStates.get(slot)
                    if (state == SLOT_RELEASED) {
                        break
                    }
                    if (slotStates.compareAndSet(slot, state, SLOT_RELEASED)) {
                        profile.readBufferPool.release(buffers[slot])
                        break
                    }
                }
            }
        }

        private fun floorMod(start: Int, offset: Int, size: Int): Int {
            return Math.floorMod(start.toLong() + offset.toLong(), size.toLong()).toInt()
        }

        private companion object {
            const val SLOT_AVAILABLE: Int = 0
            const val SLOT_IN_FLIGHT: Int = 1
            const val SLOT_RELEASED: Int = 2
        }
    }

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
    if (this == null) return null
    if (this <= 0L) return null
    if (this == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) return null
    return this
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
