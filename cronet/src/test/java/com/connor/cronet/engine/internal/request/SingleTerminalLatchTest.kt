package com.connor.cronet.engine.internal.request

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleTerminalLatchTest {
    @Test
    fun `cancel and onFailed race keeps exactly one terminal winner`() {
        val latch = SingleTerminalLatch()
        val startGate = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val winners = AtomicInteger(0)

        repeat(2) {
            thread(name = "terminal-racer-$it") {
                startGate.await()
                if (latch.tryEnterTerminal()) {
                    winners.incrementAndGet()
                }
                finished.countDown()
            }
        }

        startGate.countDown()
        assertTrue("race workers must finish", finished.await(2, TimeUnit.SECONDS))
        assertTrue("terminal state must be set", latch.isTerminal)
        assertEquals("exactly one terminal winner expected", 1, winners.get())
    }
}
