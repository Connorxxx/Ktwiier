package com.connor.cronet.engine.internal.request

import com.connor.cronet.engine.internal.fault.CronetInvariantRecorder
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.chromium.net.UrlRequest

internal sealed interface TransportCommand {
    data object Start : TransportCommand
    data class Cancel(val cause: Throwable?) : TransportCommand
    data class Read(val buffer: ByteBuffer) : TransportCommand
    data object FollowRedirect : TransportCommand
}

internal enum class LaneState {
    IDLE,
    STARTED,
    CANCELED,
}

/**
 * Serializes all [UrlRequest] method calls through a single drain loop on the callback executor.
 *
 * Key invariant: at most one drain task runs on the executor at any time.
 * Cancel before Start is deterministic -- Start is dropped and [onPreCanceled] fires.
 */
internal class RequestCommandLane(
    private val request: UrlRequest,
    private val executor: Executor,
    private val requestKey: Long,
    private val invariantRecorder: CronetInvariantRecorder,
    private val onPreCanceled: () -> Unit,
    private val onBufferRecycled: (ByteBuffer) -> Unit,
    private val onCommandFailed: (TransportCommand, Throwable) -> Unit,
) {
    private val commandQueue = ConcurrentLinkedQueue<TransportCommand>()
    private val draining = AtomicBoolean(false)
    private val state = AtomicReference(LaneState.IDLE)

    fun submit(command: TransportCommand) {
        commandQueue.add(command)
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!draining.compareAndSet(false, true)) {
            return
        }
        try {
            executor.execute(::drain)
        } catch (cause: Throwable) {
            draining.set(false)
            // Executor rejected -- drain remaining commands defensively.
            drainOnRejection()
        }
    }

    private fun drain() {
        try {
            while (true) {
                val command = commandQueue.poll() ?: break
                executeCommand(command)
            }
        } finally {
            draining.set(false)
            // Re-check: a producer may have enqueued between our last poll and the CAS release.
            if (commandQueue.isNotEmpty()) {
                scheduleDrain()
            }
        }
    }

    private fun executeCommand(command: TransportCommand) {
        try {
            executeCommandUnsafe(command)
        } catch (cause: Throwable) {
            if (command is TransportCommand.Read) {
                onBufferRecycled(command.buffer)
            }
            onCommandFailed(command, cause)
        }
    }

    private fun executeCommandUnsafe(command: TransportCommand) {
        when (command) {
            is TransportCommand.Start -> {
                when (state.get()) {
                    LaneState.CANCELED -> {
                        runCatching { invariantRecorder.onStartAfterCancel(requestKey) }
                        onPreCanceled()
                    }

                    LaneState.IDLE -> {
                        state.set(LaneState.STARTED)
                        request.start()
                    }

                    LaneState.STARTED -> {
                        // Double start -- should not happen, but idempotent guard.
                    }
                }
            }

            is TransportCommand.Cancel -> {
                val previous = state.getAndSet(LaneState.CANCELED)
                if (previous == LaneState.STARTED) {
                    request.cancel()
                }
                // If IDLE, cancel arrived before start -- onPreCanceled will fire when Start arrives.
            }

            is TransportCommand.Read -> {
                if (state.get() == LaneState.CANCELED) {
                    onBufferRecycled(command.buffer)
                    return
                }
                request.read(command.buffer)
            }

            is TransportCommand.FollowRedirect -> {
                if (state.get() == LaneState.CANCELED) {
                    return
                }
                request.followRedirect()
            }
        }
    }

    /**
     * Called when the executor rejects the drain task.
     * Recycles any pending Read buffers and signals failure to prevent leaks and hangs.
     */
    private fun drainOnRejection() {
        val rejectionCause = java.util.concurrent.RejectedExecutionException(
            "Cronet callback executor rejected drain task",
        )
        while (true) {
            val command = commandQueue.poll() ?: break
            if (command is TransportCommand.Read) {
                onBufferRecycled(command.buffer)
            }
        }
        onCommandFailed(TransportCommand.Cancel(rejectionCause), rejectionCause)
    }
}
