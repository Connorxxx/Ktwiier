package com.connor.cronet.engine.internal.request.pump

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class DirectByteBufferPool(
    private val bufferSizeBytes: Int,
    private val maxPooledBuffers: Int,
) {
    private val pooledCount = AtomicInteger(0)
    private val buffers = ConcurrentLinkedQueue<ByteBuffer>()

    fun acquire(): ByteBuffer {
        val pooled = buffers.poll()
        if (pooled != null) {
            pooledCount.decrementAndGet()
            return pooled.apply { clear() }
        }

        return ByteBuffer.allocateDirect(bufferSizeBytes)
    }

    fun release(buffer: ByteBuffer) {
        if (!buffer.isDirect || buffer.capacity() != bufferSizeBytes) {
            return
        }

        buffer.clear()
        while (true) {
            val current = pooledCount.get()
            if (current >= maxPooledBuffers) {
                return
            }
            if (pooledCount.compareAndSet(current, current + 1)) {
                buffers.offer(buffer)
                return
            }
        }
    }
}
