package com.connor.cronet.engine.internal.request.mapping

import java.io.IOException
import java.nio.ByteBuffer
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink

internal class ByteArrayUploadDataProvider(
    private val payload: ByteArray,
) : UploadDataProvider() {
    private var readOffset: Int = 0

    override fun getLength(): Long = payload.size.toLong()

    @Synchronized
    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        try {
            val remaining = payload.size - readOffset
            if (remaining <= 0) {
                uploadDataSink.onReadError(IOException("Cronet requested upload read after payload was fully consumed"))
                return
            }

            if (!byteBuffer.hasRemaining()) {
                uploadDataSink.onReadError(IOException("Cronet provided an upload buffer with no remaining capacity"))
                return
            }

            val toRead = minOf(byteBuffer.remaining(), remaining)
            byteBuffer.put(payload, readOffset, toRead)
            readOffset += toRead

            uploadDataSink.onReadSucceeded(false)
        } catch (t: Throwable) {
            uploadDataSink.onReadError((t as? Exception) ?: IOException("Upload read failed", t))
        }
    }

    @Synchronized
    override fun rewind(uploadDataSink: UploadDataSink) {
        readOffset = 0
        uploadDataSink.onRewindSucceeded()
    }

    override fun close() = Unit
}
