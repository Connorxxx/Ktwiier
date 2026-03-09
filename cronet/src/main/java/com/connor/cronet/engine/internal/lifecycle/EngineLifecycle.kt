package com.connor.cronet.engine.internal.lifecycle

import io.ktor.client.engine.ClientEngineClosedException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal sealed interface EngineState {
    data object Created : EngineState
    data object Running : EngineState
    data object Closing : EngineState
    data object Closed : EngineState
    data class CloseFailed(val activeRequests: Int) : EngineState
}

internal fun interface ActiveRequestHandle {
    fun cancel(cause: Throwable?)
}

internal class EngineLifecycle {
    private val state = AtomicReference<EngineState>(EngineState.Created)
    private val requestIdSequence = AtomicLong(0L)
    private val activeRequestCount = AtomicInteger(0)
    private val activeRequests = ConcurrentHashMap<Long, ActiveRequestHandle>()
    private val drainLock = ReentrantLock()
    private val drained = drainLock.newCondition()

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
                decrementActiveRequestCount()
                handle.cancel(ClientEngineClosedException())
            }
            throw ClientEngineClosedException()
        }

        return requestId
    }

    fun unregisterActiveRequest(requestId: Long) {
        if (activeRequests.remove(requestId) != null) {
            decrementActiveRequestCount()
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
                is EngineState.CloseFailed -> return false
            }
        }
    }

    fun cancelAllActiveRequests(cause: Throwable? = null) {
        val cancellationCause = cause ?: ClientEngineClosedException()

        activeRequests.forEach { (_, handle) ->
            runCatching { handle.cancel(cancellationCause) }
        }
    }

    fun awaitActiveRequestsToDrain(timeoutMillis: Long): Boolean {
        if (activeRequestCount.get() == 0) {
            return true
        }

        var remainingNanos = timeoutMillis.coerceAtLeast(0L) * NANOS_PER_MILLI
        drainLock.withLock {
            while (activeRequestCount.get() > 0 && remainingNanos > 0L) {
                remainingNanos = try {
                    drained.awaitNanos(remainingNanos)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return activeRequestCount.get() == 0
        }
    }

    fun markClosed() {
        state.set(EngineState.Closed)
    }

    fun markCloseFailed(activeRequests: Int) {
        state.set(EngineState.CloseFailed(activeRequests))
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
                is EngineState.CloseFailed -> throw ClientEngineClosedException()
            }
        }
    }

    private fun isAcceptingRequests(): Boolean {
        return when (state.get()) {
            EngineState.Created, EngineState.Running -> true

            EngineState.Closing, EngineState.Closed -> false
            is EngineState.CloseFailed -> false
        }
    }

    private fun decrementActiveRequestCount() {
        val remaining = activeRequestCount.decrementAndGet()
        if (remaining == 0) {
            drainLock.withLock {
                drained.signalAll()
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
