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
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import java.nio.ByteBuffer
import java.util.IdentityHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        val isSSE = data.getCapabilityOrNull(SSECapability) != null
        val responseStreamProfile = if (isSSE) sseStreamProfile else defaultStreamProfile
        val transportOwner = if (isSSE) {
            TransportOwner.StreamSession
        } else {
            TransportOwner.RequestCall(
                job = checkNotNull(callContext[Job]) { "Ktor call context must include a Job" },
            )
        }
        val requestStartNanos = System.nanoTime()
        val requestKey = REQUEST_KEY_SEQUENCE.incrementAndGet()
        val requestStartedAt = GMTDate()
        val responseDeferred = CompletableDeferred<HttpResponseData>(callContext[Job])
        val responseBodyChannel = ByteChannel(autoFlush = true)

        val callbackBridgeContext = callContext.minusKey(Job)
        val bodyWriteScope = CoroutineScope(
            callbackBridgeContext + SupervisorJob() + CoroutineName("cronet-body-writer"),
        )
        val bodyWriter = BodyWriterLoop(
            channel = responseBodyChannel,
            scope = bodyWriteScope,
        )

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
            bodyWriter = bodyWriter,
            bodyWriteScope = bodyWriteScope,
        )

        fun failRequestBeforeCallbackExecution(cause: Throwable): Nothing {
            callback.dispose(cause)
            lifecycleHandle.markTransportTerminal()
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

        val lane = RequestCommandLane(
            request = urlRequest,
            executor = callbackExecutor,
            requestKey = requestKey,
            invariantRecorder = invariantRecorder,
            onPreCanceled = {
                runCatching { invariantRecorder.onStartAfterCancel(requestKey) }
                val cancellation = CancellationException("Cronet request canceled before start")
                callback.dispose(cancellation)
                lifecycleHandle.markTransportTerminal()
                responseBodyChannel.cancel(cancellation)
                responseDeferred.completeExceptionally(cancellation)
                injectFault(
                    faultInjector = faultInjector,
                    requestKey = requestKey,
                    requestData = data,
                    phase = CronetRequestPhase.StartDroppedPreCanceled,
                )
                emitRequestTelemetry(
                    telemetry = telemetry,
                    requestData = data,
                    requestStartNanos = requestStartNanos,
                    completionReason = CronetRequestCompletionReason.Canceled,
                    responseInfo = null,
                    cause = cancellation,
                )
            },
            onBufferRecycled = { buffer ->
                buffer.clear()
                callback.readCreditRing.recycle(buffer)
            },
        )

        callback.bindLane(lane)
        lifecycleHandle.bindTransportCanceler { cause -> lane.submit(TransportCommand.Cancel(cause)) }

        // R4: For RequestCall, bind callContext cancellation. For StreamSession, skip -- SSE transport
        // lifetime is independent of the call context.
        when (transportOwner) {
            is TransportOwner.RequestCall -> {
                transportOwner.job.invokeOnCompletion { cause ->
                    if (cause != null) {
                        lane.submit(TransportCommand.Cancel(cause))
                    }
                }
            }

            TransportOwner.StreamSession -> {
                // SSE: do not bind callContext cancellation to transport.
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

        lane.submit(TransportCommand.Start)

        return try {
            responseDeferred.await()
        } catch (cause: Throwable) {
            lane.submit(TransportCommand.Cancel(cause))
            throw cause
        }
    }

    internal class CronetUrlRequestCallback(
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
        private val bodyWriter: BodyWriterLoop,
        private val bodyWriteScope: CoroutineScope,
    ) : UrlRequest.Callback() {
        private val terminal = SingleTerminalLatch()
        private val responseStarted = AtomicBoolean(false)
        @Volatile
        private var latestResponseInfo: UrlResponseInfo? = null

        internal val readCreditRing = ReadCreditRing(responseStreamProfile)
        private val readSchedulingLock = Any()
        private var awaitingReadCredit: Boolean = false
        private var activeReadBuffer: ByteBuffer? = null
        private val successFinalized = AtomicBoolean(false)

        @Volatile
        private var lane: RequestCommandLane? = null

        fun bindLane(lane: RequestCommandLane) {
            this.lane = lane
        }

        fun onBeforeRequestStart(): Throwable? {
            return injectFault(phase = CronetRequestPhase.BeforeStart)
        }

        fun dispose(cause: Throwable?) {
            bodyWriter.cancelWriter(cause)
            bodyWriteScope.cancel(
                cause as? CancellationException ?: CancellationException("Cronet request disposed"),
            )
            releaseReadBuffers()
        }

        override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, _newLocationUrl: String) {
            if (terminal.isTerminal || !responseStarted.compareAndSet(false, true)) return
            latestResponseInfo = info

            // Surface 3xx to Ktor's HttpRedirect plugin instead of following in-engine.
            val responseData = kotlin.runCatching {
                buildHttpResponseData(info)
            }.getOrElse { cause ->
                finishFailure(cause, info)
                return
            }

            if (!responseDeferred.complete(responseData)) {
                val cancellationCause = callCancellationOrDefault(
                    message = "Cronet redirect response was canceled before completion",
                )
                finishFailure(cancellationCause, info)
                return
            }

            // Close body channel (empty for redirect) and cancel Cronet transport.
            // Do NOT call markTransportTerminal() here -- wait for Cronet's onCanceled callback (R3).
            responseBodyChannel.close()
            lane?.submit(
                TransportCommand.Cancel(
                    CancellationException("Cronet redirect response handed off to Ktor redirect pipeline"),
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
                lane?.submit(TransportCommand.Cancel(injectedFailure))
                return
            }

            val responseData = kotlin.runCatching {
                buildHttpResponseData(info)
            }.getOrElse { cause ->
                finishFailure(cause)
                lane?.submit(TransportCommand.Cancel(cause))
                return
            }

            if (!responseDeferred.complete(responseData)) {
                val cancellationCause = callCancellationOrDefault(
                    message = "Cronet response was canceled before response start completed",
                )
                finishFailure(cancellationCause)
                lane?.submit(TransportCommand.Cancel(cancellationCause))
                return
            }

            val nextReadBuffer = readCreditRing.acquireForRead()
            if (nextReadBuffer == null) {
                val cause = IllegalStateException(
                    "Cronet read credit ring could not provide initial read buffer",
                )
                finishFailure(cause, info)
                lane?.submit(TransportCommand.Cancel(cause))
                return
            }
            lane?.submit(TransportCommand.Read(nextReadBuffer))
        }

        override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
            clearActiveReadBuffer(byteBuffer)
            if (terminal.isTerminal) {
                readCreditRing.recycle(byteBuffer)
                return
            }

            byteBuffer.flip()
            val chunkSizeBytes = byteBuffer.remaining().takeIf { it > 0 }

            val injectedFailure = injectFault(
                phase = CronetRequestPhase.ReadCompleted,
                bodyChunkSizeBytes = chunkSizeBytes,
            )
            if (injectedFailure != null) {
                readCreditRing.recycle(byteBuffer)
                finishFailure(injectedFailure, info)
                lane?.submit(TransportCommand.Cancel(injectedFailure))
                return
            }

            if (!byteBuffer.hasRemaining()) {
                byteBuffer.clear()
                readCreditRing.recycle(byteBuffer)
                scheduleReadOrAwaitCredit()
                return
            }

            // Send chunk to body writer. onDrained fires after writeFully completes.
            bodyWriter.send(
                BodyEvent.Chunk(
                    buffer = byteBuffer,
                    onDrained = {
                        injectFault(phase = CronetRequestPhase.BodyChunkDrained, bodyChunkSizeBytes = chunkSizeBytes)
                        byteBuffer.clear()
                        readCreditRing.recycle(byteBuffer)
                        if (!terminal.isTerminal) {
                            scheduleReadIfAwaitingCredit()
                        }
                    },
                ),
            )

            // Try to acquire next buffer for pipelining.
            scheduleReadOrAwaitCredit()
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            onTransportTerminal()
            finishSuccess(info)
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
            onTransportTerminal()
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
            onTransportTerminal()
            finishFailure(
                callCancellationOrDefault(message = "Cronet request was canceled"),
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
            injectFault(phase = CronetRequestPhase.TerminalSucceeded)

            latestResponseInfo = responseInfo
            clearActiveReadBuffer(null)
            lifecycleHandle.markTransportTerminal()

            // R2: Signal transport success to body writer. Writer drains all accepted chunks, then closes channel.
            bodyWriter.send(BodyEvent.TransportSucceeded)

            emitRequestTelemetry(
                telemetry = telemetry,
                requestData = requestData,
                requestStartNanos = requestStartNanos,
                completionReason = CronetRequestCompletionReason.Succeeded,
                responseInfo = responseInfo,
                cause = null,
            )
            lifecycleHandle.markDeliveryComplete()
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
            clearActiveReadBuffer(null)
            lifecycleHandle.markTransportTerminal()

            bodyWriter.cancelWriter(cause)
            bodyWriteScope.cancel(
                cause as? CancellationException ?: CancellationException(
                    "Cronet request bridge scopes canceled",
                ).apply { initCause(cause) },
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
            lifecycleHandle.markDeliveryComplete()
        }

        private fun onTransportTerminal() {
            clearActiveReadBuffer(null)?.let(readCreditRing::recycle)
            releaseReadBuffers()
        }

        private fun releaseReadBuffers() {
            readCreditRing.closeAndReleaseAvailable()
        }

        private fun scheduleReadOrAwaitCredit() {
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

            synchronized(readSchedulingLock) {
                if (terminal.isTerminal) {
                    readCreditRing.recycle(nextBuffer)
                    return
                }
                activeReadBuffer = nextBuffer
            }
            lane?.submit(TransportCommand.Read(nextBuffer))
        }

        private fun scheduleReadIfAwaitingCredit() {
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

            synchronized(readSchedulingLock) {
                if (terminal.isTerminal) {
                    readCreditRing.recycle(nextBuffer)
                    return
                }
                activeReadBuffer = nextBuffer
            }
            lane?.submit(TransportCommand.Read(nextBuffer))
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

    internal data class ResponseStreamProfile(
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

    internal class ReadCreditRing(
        private val profile: ResponseStreamProfile,
    ) {
        private val capacity = profile.maxInFlightBuffers
        private val buffers: Array<ByteBuffer> = Array(capacity) {
            profile.readBufferPool.acquire()
        }
        private val slotStates: AtomicIntegerArray = AtomicIntegerArray(capacity)
        private val acquireCursor: AtomicInteger = AtomicInteger(0)
        private val closed = AtomicBoolean(false)
        private val bufferSlotIndex = IdentityHashMap<ByteBuffer, Int>(capacity)

        init {
            buffers.forEachIndexed { index, buffer ->
                bufferSlotIndex[buffer] = index
            }
        }

        fun acquireForRead(): ByteBuffer? {
            if (closed.get()) {
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
            val slot = bufferSlotIndex[buffer] ?: return
            while (true) {
                when (slotStates.get(slot)) {
                    SLOT_IN_FLIGHT -> {
                        if (closed.get()) {
                            if (slotStates.compareAndSet(slot, SLOT_IN_FLIGHT, SLOT_RELEASED)) {
                                profile.readBufferPool.release(buffers[slot])
                                return
                            }
                        } else if (slotStates.compareAndSet(slot, SLOT_IN_FLIGHT, SLOT_AVAILABLE)) {
                            buffer.clear()
                            return
                        }
                    }

                    SLOT_AVAILABLE -> {
                        if (!closed.get()) {
                            return
                        }
                        if (slotStates.compareAndSet(slot, SLOT_AVAILABLE, SLOT_RELEASED)) {
                            profile.readBufferPool.release(buffers[slot])
                            return
                        }
                    }

                    SLOT_RELEASED -> return
                    else -> return
                }
            }
        }

        fun closeAndReleaseAvailable() {
            closed.set(true)
            releaseAvailableSlots()
        }

        private fun releaseAvailableSlots() {
            for (slot in 0 until capacity) {
                while (true) {
                    val state = slotStates.get(slot)
                    if (state == SLOT_RELEASED) {
                        break
                    }
                    if (state != SLOT_AVAILABLE) {
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
}

private fun injectFault(
    faultInjector: CronetFaultInjector,
    requestKey: Long,
    requestData: HttpRequestData,
    phase: CronetRequestPhase,
) {
    runCatching {
        faultInjector.onRequestPhase(
            CronetRequestFaultContext(
                requestKey = requestKey,
                method = requestData.method.value,
                url = requestData.url.toString(),
                phase = phase,
            ),
        )
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
