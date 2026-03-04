package com.connor.cronet.engine.internal.fault

import com.connor.cronet.engine.internal.telemetry.CronetRequestCompletionReason
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal interface CronetInvariantRecorder {
    fun onRequestRegistered(requestId: Long, activeRequestCount: Int) = Unit

    fun onRequestUnregistered(requestId: Long, activeRequestCount: Int) = Unit

    fun onTerminalEvent(
        requestKey: Long,
        completionReason: CronetRequestCompletionReason,
        duplicate: Boolean,
    ) = Unit

    fun onBodyQueueOverflow(requestKey: Long, queueCapacity: Int) = Unit

    fun onCloseDrainTimeout(activeRequestCount: Int) = Unit
}

internal data object NoopCronetInvariantRecorder : CronetInvariantRecorder

internal data class CronetInvariantSnapshot(
    val registeredRequestCount: Long,
    val unregisteredRequestCount: Long,
    val terminalEventCount: Long,
    val duplicateTerminalEventCount: Long,
    val bodyQueueOverflowCount: Long,
    val closeDrainTimeoutCount: Long,
    val maxObservedActiveRequestCount: Int,
)

internal class AtomicCronetInvariantRecorder : CronetInvariantRecorder {
    private val registeredRequestCount = AtomicLong(0L)
    private val unregisteredRequestCount = AtomicLong(0L)
    private val terminalEventCount = AtomicLong(0L)
    private val duplicateTerminalEventCount = AtomicLong(0L)
    private val bodyQueueOverflowCount = AtomicLong(0L)
    private val closeDrainTimeoutCount = AtomicLong(0L)
    private val maxObservedActiveRequestCount = AtomicInteger(0)

    override fun onRequestRegistered(requestId: Long, activeRequestCount: Int) {
        registeredRequestCount.incrementAndGet()
        updateMaxActiveCount(activeRequestCount)
    }

    override fun onRequestUnregistered(requestId: Long, activeRequestCount: Int) {
        unregisteredRequestCount.incrementAndGet()
        updateMaxActiveCount(activeRequestCount)
    }

    override fun onTerminalEvent(
        requestKey: Long,
        completionReason: CronetRequestCompletionReason,
        duplicate: Boolean,
    ) {
        if (duplicate) {
            duplicateTerminalEventCount.incrementAndGet()
        } else {
            terminalEventCount.incrementAndGet()
        }
    }

    override fun onBodyQueueOverflow(requestKey: Long, queueCapacity: Int) {
        bodyQueueOverflowCount.incrementAndGet()
    }

    override fun onCloseDrainTimeout(activeRequestCount: Int) {
        closeDrainTimeoutCount.incrementAndGet()
        updateMaxActiveCount(activeRequestCount)
    }

    fun snapshot(): CronetInvariantSnapshot {
        return CronetInvariantSnapshot(
            registeredRequestCount = registeredRequestCount.get(),
            unregisteredRequestCount = unregisteredRequestCount.get(),
            terminalEventCount = terminalEventCount.get(),
            duplicateTerminalEventCount = duplicateTerminalEventCount.get(),
            bodyQueueOverflowCount = bodyQueueOverflowCount.get(),
            closeDrainTimeoutCount = closeDrainTimeoutCount.get(),
            maxObservedActiveRequestCount = maxObservedActiveRequestCount.get(),
        )
    }

    private fun updateMaxActiveCount(candidate: Int) {
        while (true) {
            val current = maxObservedActiveRequestCount.get()
            if (candidate <= current) {
                return
            }
            if (maxObservedActiveRequestCount.compareAndSet(current, candidate)) {
                return
            }
        }
    }
}
