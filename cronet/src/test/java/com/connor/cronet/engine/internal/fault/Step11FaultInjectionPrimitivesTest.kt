package com.connor.cronet.engine.internal.fault

import com.connor.cronet.engine.internal.telemetry.CronetRequestCompletionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Step11FaultInjectionPrimitivesTest {
    @Test
    fun `deterministic fault injector throws on configured invocation only`() {
        val injector = DeterministicCronetFaultInjector(
            plan = mapOf(
                CronetRequestPhase.ReadCompleted to listOf(
                    FaultTrigger(invocationIndex = 1) { context ->
                        IllegalStateException("injected-${context.phase}-${context.requestKey}")
                    },
                ),
            ),
        )

        val context = CronetRequestFaultContext(
            requestKey = 7L,
            method = "GET",
            url = "https://example.com/sse",
            phase = CronetRequestPhase.ReadCompleted,
            bodyChunkSizeBytes = 128,
        )

        assertNull(runCatching { injector.onRequestPhase(context) }.exceptionOrNull())

        val secondFailure = assertThrows(IllegalStateException::class.java) {
            injector.onRequestPhase(context)
        }
        assertEquals("injected-ReadCompleted-7", secondFailure.message)

        assertNull(runCatching { injector.onRequestPhase(context) }.exceptionOrNull())
    }

    @Test
    fun `atomic invariant recorder snapshots matrix counters`() {
        val recorder = AtomicCronetInvariantRecorder()

        recorder.onRequestRegistered(requestId = 1L, activeRequestCount = 1)
        recorder.onRequestRegistered(requestId = 2L, activeRequestCount = 2)
        recorder.onBodyQueueOverflow(requestKey = 2L, queueCapacity = 64)
        recorder.onTerminalEvent(
            requestKey = 2L,
            completionReason = CronetRequestCompletionReason.Failed,
            duplicate = false,
        )
        recorder.onTerminalEvent(
            requestKey = 2L,
            completionReason = CronetRequestCompletionReason.Failed,
            duplicate = true,
        )
        recorder.onCloseDrainTimeout(activeRequestCount = 1)
        recorder.onRequestUnregistered(requestId = 2L, activeRequestCount = 1)

        val snapshot = recorder.snapshot()
        assertEquals(2L, snapshot.registeredRequestCount)
        assertEquals(1L, snapshot.unregisteredRequestCount)
        assertEquals(1L, snapshot.terminalEventCount)
        assertEquals(1L, snapshot.duplicateTerminalEventCount)
        assertEquals(1L, snapshot.bodyQueueOverflowCount)
        assertEquals(1L, snapshot.closeDrainTimeoutCount)
        assertEquals(2, snapshot.maxObservedActiveRequestCount)
    }
}
