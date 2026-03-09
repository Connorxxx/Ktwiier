package com.connor.cronet.engine.internal.fault

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal interface CronetFaultInjector {
    fun onEngineCloseStarted(activeRequestCount: Int) = Unit

    fun onRequestPhase(context: CronetRequestFaultContext) = Unit
}

internal enum class CronetRequestPhase {
    BeforeStart,
    StartDroppedPreCanceled,
    ResponseStarted,
    ReadCompleted,
    BodyChunkDrained,
    TerminalSucceeded,
    TerminalFailed,
    TerminalCanceled,
}

internal data class CronetRequestFaultContext(
    val requestKey: Long,
    val method: String,
    val url: String,
    val phase: CronetRequestPhase,
    val bodyChunkSizeBytes: Int? = null,
    val cause: Throwable? = null,
)

internal data object NoopCronetFaultInjector : CronetFaultInjector

/**
 * Deterministic fault injector for repeatable scenario tests.
 * Each phase can be configured to throw on selected invocation indices (0-based).
 */
internal class DeterministicCronetFaultInjector(
    private val plan: Map<CronetRequestPhase, List<FaultTrigger>>,
) : CronetFaultInjector {
    private val phaseInvocationCounters = ConcurrentHashMap<CronetRequestPhase, AtomicInteger>()

    override fun onRequestPhase(context: CronetRequestFaultContext) {
        val phase = context.phase
        val invocation = phaseInvocationCounters
            .computeIfAbsent(phase) { AtomicInteger(0) }
            .getAndIncrement()

        val configuredTriggers = plan[phase].orEmpty()
        val trigger = configuredTriggers.firstOrNull { it.invocationIndex == invocation } ?: return
        throw trigger.failureFactory(context)
    }
}

internal data class FaultTrigger(
    val invocationIndex: Int,
    val failureFactory: (CronetRequestFaultContext) -> Throwable,
)
