package com.connor.cronet.engine

import com.connor.cronet.engine.internal.CronetEngineFactory
import com.connor.cronet.engine.internal.fault.CronetFaultInjector
import com.connor.cronet.engine.internal.fault.CronetInvariantRecorder
import com.connor.cronet.engine.internal.lifecycle.ActiveRequestHandle
import com.connor.cronet.engine.internal.lifecycle.EngineLifecycle
import com.connor.cronet.engine.internal.lifecycle.EngineState
import com.connor.cronet.engine.internal.request.CronetRequestLifecycleHandle
import com.connor.cronet.engine.internal.request.CronetRequestExecutor
import com.connor.cronet.engine.internal.request.DefaultCronetRequestExecutor
import com.connor.cronet.engine.internal.telemetry.CronetTelemetry
import com.connor.cronet.engine.internal.telemetry.NoopCronetTelemetry
import io.ktor.client.engine.ClientEngineClosedException
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(
        SSECapability,
    )

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
    private val faultInjector: CronetFaultInjector = config.faultInjector
    private val invariantRecorder: CronetInvariantRecorder = config.invariantRecorder

    private val requestExecutor: CronetRequestExecutor = DefaultCronetRequestExecutor(
        cronetEngine = cronetEngine,
        callbackExecutor = callbackExecutor,
        defaultResponseStreamProfile = config.defaultResponseStreamProfile,
        sseResponseStreamProfile = config.sseResponseStreamProfile,
        telemetry = telemetry,
        faultInjector = faultInjector,
        invariantRecorder = invariantRecorder,
    )

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val requestCallContext = callContext()
        val requestHandle = EngineActiveRequestHandle(callContext = requestCallContext)
        val requestId = lifecycle.registerActiveRequest(
            handle = requestHandle,
        )
        recordInvariant {
            invariantRecorder.onRequestRegistered(
                requestId = requestId,
                activeRequestCount = lifecycle.currentActiveRequestCount,
            )
        }

        val lifecycleHandle = EngineRequestLifecycleHandle(
            requestHandle = requestHandle,
            onTransportTerminal = {
                lifecycle.unregisterActiveRequest(requestId)
                recordInvariant {
                    invariantRecorder.onRequestUnregistered(
                        requestId = requestId,
                        activeRequestCount = lifecycle.currentActiveRequestCount,
                    )
                }
            },
        )

        return runCatching {
            requestExecutor.execute(
                data = data,
                callContext = requestCallContext,
                lifecycleHandle = lifecycleHandle,
            )
        }.getOrElse { cause ->
            lifecycleHandle.markTransportTerminal()
            throw cause
        }
    }

    override fun close() {
        if (!lifecycle.startClosing()) {
            return
        }

        super.close()

        val closeCause = ClientEngineClosedException()
        try {
            injectCloseFault(activeRequestCount = lifecycle.currentActiveRequestCount)
            lifecycle.cancelAllActiveRequests(cause = closeCause)

            val drainedInInitialWindow = lifecycle.awaitActiveRequestsToDrain(
                timeoutMillis = config.closeDrainTimeoutMillis,
            )
            val drained = if (drainedInInitialWindow) {
                true
            } else {
                lifecycle.cancelAllActiveRequests(cause = closeCause)
                lifecycle.awaitActiveRequestsToDrain(
                    timeoutMillis = config.closeForceDrainTimeoutMillis,
                )
            }

            if (!drained) {
                recordInvariant {
                    invariantRecorder.onCloseDrainTimeout(lifecycle.currentActiveRequestCount)
                }
                reportEngineShutdownFailure(
                    IllegalStateException(
                        "Cronet engine close timed out while waiting for active requests to drain " +
                            "[active_requests=${lifecycle.currentActiveRequestCount}]",
                    ),
                )
                lifecycle.markCloseFailed(activeRequests = lifecycle.currentActiveRequestCount)
                return
            }

            runCatching { requestExecutor.close() }
                .onFailure(::reportEngineShutdownFailure)
            runCatching { cronetEngine.shutdown() }
                .onFailure(::reportEngineShutdownFailure)
            runCatching { callbackExecutor.shutdown() }
                .onFailure(::reportEngineShutdownFailure)
        } finally {
            if (lifecycle.currentState is EngineState.Closing) {
                lifecycle.markClosed()
            }
        }
    }

    private fun reportEngineShutdownFailure(cause: Throwable) {
        runCatching { telemetry.onEngineShutdownFailure(cause) }
    }

    private fun injectCloseFault(activeRequestCount: Int) {
        runCatching {
            faultInjector.onEngineCloseStarted(activeRequestCount)
        }.onFailure(::reportEngineShutdownFailure)
    }

    private fun recordInvariant(record: () -> Unit) {
        runCatching(record)
    }

    private class EngineActiveRequestHandle(callContext: CoroutineContext) : ActiveRequestHandle {
        private val callJob = checkNotNull(callContext[Job]) {
            "Ktor call context must include a Job"
        }
        private val cancelInvoked = AtomicBoolean(false)
        private val cancellationCause = AtomicReference<Throwable?>(null)

        @Volatile
        private var transportCanceler: ((Throwable?) -> Unit)? = null

        fun bindTransportCanceler(canceler: (Throwable?) -> Unit) {
            transportCanceler = canceler
            if (cancelInvoked.get()) {
                canceler(cancellationCause.get())
            }
        }

        override fun cancel(cause: Throwable?) {
            if (!cancelInvoked.compareAndSet(false, true)) {
                return
            }
            cancellationCause.set(cause)
            transportCanceler?.invoke(cause)

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

    private class EngineRequestLifecycleHandle(
        private val requestHandle: EngineActiveRequestHandle,
        private val onTransportTerminal: () -> Unit,
    ) : CronetRequestLifecycleHandle {
        private val transportTerminalMarked = AtomicBoolean(false)
        private val deliveryCompleteMarked = AtomicBoolean(false)

        override fun bindTransportCanceler(canceler: (Throwable?) -> Unit) {
            requestHandle.bindTransportCanceler(canceler)
        }

        override fun markTransportTerminal() {
            if (!transportTerminalMarked.compareAndSet(false, true)) {
                return
            }
            onTransportTerminal()
        }

        override fun markDeliveryComplete() {
            if (!deliveryCompleteMarked.compareAndSet(false, true)) {
                return
            }
        }
    }
}
