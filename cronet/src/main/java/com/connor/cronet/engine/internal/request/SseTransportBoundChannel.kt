package com.connor.cronet.engine.internal.request

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.io.Source
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps the SSE response body channel to bind transport cancellation to session lifetime.
 *
 * When `DefaultClientSSESession.close()` cancels its `input` channel, this wrapper intercepts
 * that cancellation and submits [TransportCommand.Cancel] to the request lane, ensuring the
 * underlying `UrlRequest` is terminated promptly -- even during idle periods with no in-flight writes.
 *
 * The existing `onChannelWriteFailed` bridge in [BodyWriterLoop] remains as defense-in-depth
 * for active-write teardown, but this wrapper is the primary ownership path for idle-close.
 */
@OptIn(InternalAPI::class)
internal class SseTransportBoundChannel(
    private val delegate: ByteReadChannel,
    private val onCancel: (Throwable?) -> Unit,
) : ByteReadChannel {
    private val cancelSubmitted = AtomicBoolean(false)

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Source
        get() = delegate.readBuffer

    override suspend fun awaitContent(min: Int): Boolean =
        delegate.awaitContent(min)

    override fun cancel(cause: Throwable?) {
        if (cancelSubmitted.compareAndSet(false, true)) {
            onCancel(cause)
        }
        delegate.cancel(cause)
    }
}
