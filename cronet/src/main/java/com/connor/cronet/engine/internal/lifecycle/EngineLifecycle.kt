package com.connor.cronet.engine.internal.lifecycle

import io.ktor.client.engine.ClientEngineClosedException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal sealed interface EngineState {
    data object Created : EngineState
    data object Running : EngineState
    data object Closing : EngineState
    data object Closed : EngineState
}

internal fun interface ActiveRequestHandle {
    fun cancel(cause: Throwable?)
}

internal class EngineLifecycle {
    private val state = AtomicReference<EngineState>(EngineState.Created)
    private val requestIdSequence = AtomicLong(0L)
    private val activeRequestCount = AtomicInteger(0)
    private val activeRequests = ConcurrentHashMap<Long, ActiveRequestHandle>()

    fun registerActiveRequest(handle: ActiveRequestHandle): Long {
        ensureAcceptingRequests()

        val requestId = requestIdSequence.incrementAndGet()
        check(activeRequests.putIfAbsent(requestId, handle) == null) {
            "Duplicate active request id: $requestId"
        }
        activeRequestCount.incrementAndGet()

        // Close may win the race after registration; force immediate self-cancel in that case.
        if (!isAcceptingRequests()) {
            if (activeRequests.remove(requestId, handle)) {
                activeRequestCount.decrementAndGet()
                handle.cancel(ClientEngineClosedException())
            }
            throw ClientEngineClosedException()
        }

        return requestId
    }

    fun unregisterActiveRequest(requestId: Long) {
        if (activeRequests.remove(requestId) != null) {
            activeRequestCount.decrementAndGet()
        }
    }

    fun startClosing(): Boolean {
        while (true) {
            when (val current = state.get()) {
                EngineState.Created, EngineState.Running -> {
                    if (state.compareAndSet(current, EngineState.Closing)) {
                        return true
                    }
                }

                EngineState.Closing, EngineState.Closed -> return false
            }
        }
    }

    fun cancelAllActiveRequests(cause: Throwable? = null) {
        val cancellationCause = cause ?: ClientEngineClosedException()

        activeRequests.forEach { requestId, handle ->
            if (activeRequests.remove(requestId, handle)) {
                activeRequestCount.decrementAndGet()
                runCatching { handle.cancel(cancellationCause) }
            }
        }
    }

    fun markClosed() {
        state.set(EngineState.Closed)
    }

    val currentState: EngineState
        get() = state.get()

    val currentActiveRequestCount: Int
        get() = activeRequestCount.get()

    private fun ensureAcceptingRequests() {
        while (true) {
            when (val current = state.get()) {
                EngineState.Created -> {
                    if (state.compareAndSet(current, EngineState.Running)) {
                        return
                    }
                }

                EngineState.Running -> return
                EngineState.Closing, EngineState.Closed -> throw ClientEngineClosedException()
            }
        }
    }

    private fun isAcceptingRequests(): Boolean {
        return when (state.get()) {
            EngineState.Created, EngineState.Running -> true

            EngineState.Closing, EngineState.Closed -> false
        }
    }
}
