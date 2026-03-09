package com.connor.cronet.engine.internal.request

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal sealed interface BodyEvent {
    data class Chunk(val buffer: ByteBuffer, val onDrained: () -> Unit) : BodyEvent
    data object TransportSucceeded : BodyEvent
    data class TransportFailed(val cause: Throwable) : BodyEvent
}

/**
 * Single-consumer coroutine that owns all writes to the response body channel.
 *
 * Key invariant: exactly one coroutine writes to the body channel.
 * Success closes channel only after all accepted chunks are written.
 */
internal class BodyWriterLoop(
    private val channel: ByteWriteChannel,
    scope: CoroutineScope,
) {
    private val mailbox = Channel<BodyEvent>(Channel.UNLIMITED)
    private val writerJob: Job = scope.launch { writeLoop() }

    private suspend fun writeLoop() {
        for (event in mailbox) {
            when (event) {
                is BodyEvent.Chunk -> {
                    try {
                        channel.writeFully(event.buffer)
                    } catch (cause: Throwable) {
                        event.onDrained()
                        // Channel write failed -- drain remaining events and cancel.
                        drainMailboxOnFailure(cause)
                        channel.cancel(cause)
                        return
                    }
                    event.onDrained()
                }

                is BodyEvent.TransportSucceeded -> {
                    @Suppress("DEPRECATION")
                    channel.close()
                    return
                }

                is BodyEvent.TransportFailed -> {
                    channel.cancel(event.cause)
                    return
                }
            }
        }
        // Mailbox closed externally -- treat as cancellation.
        @Suppress("DEPRECATION")
        channel.close()
    }

    fun send(event: BodyEvent) {
        mailbox.trySend(event)
    }

    suspend fun awaitDrain() {
        writerJob.join()
    }

    fun cancelWriter(cause: Throwable?) {
        val cancellation = cause as? CancellationException
            ?: CancellationException("BodyWriterLoop canceled").apply {
                if (cause != null) initCause(cause)
            }
        writerJob.cancel(cancellation)
        mailbox.close()
    }

    /**
     * Drain remaining Chunk events so their onDrained callbacks fire,
     * preventing buffer leaks.
     */
    private fun drainMailboxOnFailure(cause: Throwable) {
        while (true) {
            val remaining = mailbox.tryReceive().getOrNull() ?: break
            if (remaining is BodyEvent.Chunk) {
                remaining.onDrained()
            }
        }
    }
}
