package com.connor.cronet.engine.internal.fault

import com.connor.cronet.engine.internal.request.SingleTerminalLatch
import com.connor.cronet.engine.internal.telemetry.CronetRequestCompletionReason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step11ScenarioMatrixTest {
    @Test
    fun `callback thread saturation overflows bounded queue and fails once`() {
        val harness = DeterministicRequestScenarioHarness(queueCapacity = 2)

        harness.emitReadChunk()
        harness.emitReadChunk()
        harness.emitReadChunk() // overflow

        val snapshot = harness.snapshot()
        assertEquals(1L, snapshot.bodyQueueOverflowCount)
        assertEquals(1L, snapshot.terminalEventCount)
        assertEquals(0L, snapshot.duplicateTerminalEventCount)
    }

    @Test
    fun `slow consumer triggers deterministic degradation without dead terminal loop`() {
        val harness = DeterministicRequestScenarioHarness(queueCapacity = 1)

        harness.emitReadChunk()
        // consumer lags and does not drain in time
        harness.emitReadChunk() // overflow -> fail fast
        harness.consumeChunk() // late drain should not affect terminal

        val snapshot = harness.snapshot()
        assertEquals(1L, snapshot.bodyQueueOverflowCount)
        assertEquals(1L, snapshot.terminalEventCount)
    }

    @Test
    fun `network switch mid stream converges to one failed terminal`() {
        val harness = DeterministicRequestScenarioHarness(queueCapacity = 8)

        harness.emitReadChunk()
        harness.consumeChunk()
        harness.fail()
        harness.fail() // late callback equivalent

        val snapshot = harness.snapshot()
        assertEquals(0L, snapshot.bodyQueueOverflowCount)
        assertEquals(1L, snapshot.terminalEventCount)
        assertEquals(1L, snapshot.duplicateTerminalEventCount)
    }

    @Test
    fun `cancel and onFailed race keeps one winner and one duplicate`() {
        val harness = DeterministicRequestScenarioHarness(queueCapacity = 8)
        val startGate = CountDownLatch(1)
        val finished = CountDownLatch(2)

        thread(name = "race-cancel") {
            startGate.await()
            harness.cancel()
            finished.countDown()
        }
        thread(name = "race-fail") {
            startGate.await()
            harness.fail()
            finished.countDown()
        }

        startGate.countDown()
        assertTrue(finished.await(2, TimeUnit.SECONDS))

        val snapshot = harness.snapshot()
        assertEquals(1L, snapshot.terminalEventCount)
        assertEquals(1L, snapshot.duplicateTerminalEventCount)
    }
}

private class DeterministicRequestScenarioHarness(
    private val queueCapacity: Int,
) {
    private val requestKey = 1L
    private val terminalLatch = SingleTerminalLatch()
    private val recorder = AtomicCronetInvariantRecorder()
    private var queueDepth = 0

    fun emitReadChunk() {
        if (terminalLatch.isTerminal) {
            return
        }

        if (queueDepth >= queueCapacity) {
            recorder.onBodyQueueOverflow(requestKey = requestKey, queueCapacity = queueCapacity)
            fail()
            return
        }

        queueDepth += 1
    }

    fun consumeChunk() {
        if (queueDepth > 0) {
            queueDepth -= 1
        }
    }

    fun fail() {
        recordTerminal(CronetRequestCompletionReason.Failed)
    }

    fun cancel() {
        recordTerminal(CronetRequestCompletionReason.Canceled)
    }

    fun snapshot(): CronetInvariantSnapshot = recorder.snapshot()

    private fun recordTerminal(reason: CronetRequestCompletionReason) {
        val won = terminalLatch.tryEnterTerminal()
        recorder.onTerminalEvent(
            requestKey = requestKey,
            completionReason = reason,
            duplicate = !won,
        )
    }
}
