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
    fun `callback thread saturation is throttled by write completion credit`() {
        val harness = CreditDrivenRequestScenarioHarness()

        harness.emitReadCompleted() // first chunk enters write bridge
        harness.emitReadCompleted() // suppressed: no read credit until write completes
        harness.emitReadCompleted() // suppressed again

        val mid = harness.snapshot()
        assertEquals(1, mid.deliveredReadCallbacks)
        assertEquals(2, mid.suppressedReadCallbacks)
        assertEquals(0L, mid.invariants.terminalEventCount)

        harness.completeWriteSuccess()
        harness.succeed()

        val final = harness.snapshot()
        assertEquals(1L, final.invariants.terminalEventCount)
        assertEquals(0L, final.invariants.duplicateTerminalEventCount)
        assertEquals(0L, final.invariants.bodyQueueOverflowCount)
    }

    @Test
    fun `slow consumer throttles transport reads without overflow failure`() {
        val harness = CreditDrivenRequestScenarioHarness()

        harness.emitReadCompleted()
        repeat(5) { harness.emitReadCompleted() } // all suppressed while writer is still busy
        harness.completeWriteSuccess() // releases one read credit

        harness.emitReadCompleted()
        harness.completeWriteSuccess()
        harness.succeed()

        val snapshot = harness.snapshot()
        assertTrue("suppressed callbacks should show backpressure", snapshot.suppressedReadCallbacks > 0)
        assertEquals(2, snapshot.deliveredReadCallbacks)
        assertEquals(0L, snapshot.invariants.bodyQueueOverflowCount)
        assertEquals(1L, snapshot.invariants.terminalEventCount)
        assertEquals(0L, snapshot.invariants.duplicateTerminalEventCount)
    }

    @Test
    fun `network switch mid stream converges to one failed terminal`() {
        val harness = CreditDrivenRequestScenarioHarness()

        harness.emitReadCompleted()
        harness.completeWriteSuccess()
        harness.fail()
        harness.fail() // late callback equivalent

        val snapshot = harness.snapshot()
        assertEquals(1L, snapshot.invariants.terminalEventCount)
        assertEquals(1L, snapshot.invariants.duplicateTerminalEventCount)
    }

    @Test
    fun `cancel and onFailed race keeps one winner and one duplicate`() {
        val harness = CreditDrivenRequestScenarioHarness()
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
        assertEquals(1L, snapshot.invariants.terminalEventCount)
        assertEquals(1L, snapshot.invariants.duplicateTerminalEventCount)
    }
}

private class CreditDrivenRequestScenarioHarness(
) {
    private val requestKey = 1L
    private val terminalLatch = SingleTerminalLatch()
    private val recorder = AtomicCronetInvariantRecorder()
    private var readCreditAvailable = true
    private var writeInFlight = false
    private var deliveredReadCallbacks = 0
    private var suppressedReadCallbacks = 0

    fun emitReadCompleted() {
        if (terminalLatch.isTerminal) {
            return
        }

        if (!readCreditAvailable) {
            suppressedReadCallbacks += 1
            return
        }

        readCreditAvailable = false
        writeInFlight = true
        deliveredReadCallbacks += 1
    }

    fun completeWriteSuccess() {
        if (terminalLatch.isTerminal) {
            return
        }

        if (writeInFlight) {
            writeInFlight = false
            readCreditAvailable = true
        }
    }

    fun succeed() {
        recordTerminal(CronetRequestCompletionReason.Succeeded)
    }

    fun fail() {
        recordTerminal(CronetRequestCompletionReason.Failed)
    }

    fun cancel() {
        recordTerminal(CronetRequestCompletionReason.Canceled)
    }

    fun snapshot(): HarnessSnapshot {
        return HarnessSnapshot(
            deliveredReadCallbacks = deliveredReadCallbacks,
            suppressedReadCallbacks = suppressedReadCallbacks,
            invariants = recorder.snapshot(),
        )
    }

    private fun recordTerminal(reason: CronetRequestCompletionReason) {
        val won = terminalLatch.tryEnterTerminal()
        recorder.onTerminalEvent(
            requestKey = requestKey,
            completionReason = reason,
            duplicate = !won,
        )
    }
}

private data class HarnessSnapshot(
    val deliveredReadCallbacks: Int,
    val suppressedReadCallbacks: Int,
    val invariants: CronetInvariantSnapshot,
)
