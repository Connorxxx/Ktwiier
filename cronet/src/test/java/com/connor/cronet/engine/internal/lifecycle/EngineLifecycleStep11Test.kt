package com.connor.cronet.engine.internal.lifecycle

import io.ktor.client.engine.ClientEngineClosedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineLifecycleStep11Test {
    @Test
    fun `close and long-lived request concurrent path drains successfully`() {
        val lifecycle = EngineLifecycle()
        val cancelCount = AtomicInteger(0)
        val requestId = lifecycle.registerActiveRequest {
            cancelCount.incrementAndGet()
        }

        assertTrue(lifecycle.startClosing())
        lifecycle.cancelAllActiveRequests(ClientEngineClosedException())
        assertEquals(1, cancelCount.get())

        val drained = AtomicBoolean(false)
        val waitFinished = CountDownLatch(1)
        val waiter = thread(name = "engine-drain-waiter") {
            drained.set(lifecycle.awaitActiveRequestsToDrain(timeoutMillis = 1_500L))
            waitFinished.countDown()
        }

        Thread.sleep(50)
        lifecycle.unregisterActiveRequest(requestId)

        assertTrue(waitFinished.await(2, TimeUnit.SECONDS))
        waiter.join(500)
        assertTrue("drain should complete after request unregister", drained.get())
        assertEquals(0, lifecycle.currentActiveRequestCount)
    }

    @Test
    fun `close drain timeout is reported when request never unregisters`() {
        val lifecycle = EngineLifecycle()
        lifecycle.registerActiveRequest { }

        assertTrue(lifecycle.startClosing())
        lifecycle.cancelAllActiveRequests(ClientEngineClosedException())

        val drained = lifecycle.awaitActiveRequestsToDrain(timeoutMillis = 20L)
        assertFalse("drain should time out with active request leak", drained)
        assertEquals(1, lifecycle.currentActiveRequestCount)
    }

    @Test
    fun `register request after closing gate is rejected`() {
        val lifecycle = EngineLifecycle()
        assertTrue(lifecycle.startClosing())

        assertThrows(ClientEngineClosedException::class.java) {
            lifecycle.registerActiveRequest { }
        }
    }
}
