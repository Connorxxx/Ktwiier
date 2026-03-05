package com.connor.cronet.engine.internal.request.mapping

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink

internal class StreamingUploadDataProvider private constructor(
    private val uploadLength: Long?,
    private val sourceFactory: UploadSourceFactory,
    private val rewindMode: RewindMode,
) : UploadDataProvider() {
    private val stateLock = Any()
    private val controlScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("cronet-upload-provider"),
    )

    private var uploadedBytes: Long = 0L
    private var activeSource: UploadSource? = null
    private var isClosed: Boolean = false

    override fun getLength(): Long = uploadLength ?: CHUNKED_UPLOAD_LENGTH

    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        if (!byteBuffer.hasRemaining()) {
            uploadDataSink.onReadError(
                IOException("Cronet provided an upload buffer with no remaining capacity"),
            )
            return
        }

        val preparedRead = prepareRead(byteBuffer.remaining())
        when (preparedRead) {
            is ReadPreparation.Error -> {
                uploadDataSink.onReadError(preparedRead.cause.asUploadException("Upload read failed"))
            }

            is ReadPreparation.Ready -> {
                controlScope.launch {
                    performRead(
                        preparedRead = preparedRead,
                        uploadDataSink = uploadDataSink,
                        targetBuffer = byteBuffer,
                    )
                }
            }
        }
    }

    override fun rewind(uploadDataSink: UploadDataSink) {
        when (val mode = rewindMode) {
            is RewindMode.Unsupported -> {
                uploadDataSink.onRewindError(IOException(mode.reason))
            }

            RewindMode.ReopenSource -> {
                controlScope.launch {
                    val previous = synchronized(stateLock) {
                        if (isClosed) {
                            uploadDataSink.onRewindError(IOException("Cronet upload provider is already closed"))
                            return@launch
                        }
                        val current = activeSource
                        activeSource = null
                        uploadedBytes = 0L
                        current
                    }

                    previous?.close(
                        IOException("Cronet upload source rewind requested by transport layer"),
                    )

                    val replacement = runCatching { sourceFactory.open() }
                        .getOrElse { cause ->
                            uploadDataSink.onRewindError(
                                cause.asUploadException("Cronet upload rewind failed"),
                            )
                            return@launch
                        }

                    synchronized(stateLock) {
                        if (isClosed) {
                            replacement.close(IOException("Cronet upload provider is already closed"))
                            uploadDataSink.onRewindError(IOException("Cronet upload provider is already closed"))
                            return@launch
                        }
                        activeSource = replacement
                    }

                    uploadDataSink.onRewindSucceeded()
                }
            }
        }
    }

    override fun close() {
        var source: UploadSource? = null
        val alreadyClosed = synchronized(stateLock) {
            if (isClosed) {
                true
            } else {
                isClosed = true
                uploadedBytes = 0L
                source = activeSource
                activeSource = null
                false
            }
        }
        if (alreadyClosed) return

        source?.close(IOException("Cronet upload provider closed"))
        controlScope.cancel()
    }

    private fun prepareRead(requestedBytes: Int): ReadPreparation {
        return synchronized(stateLock) {
            if (isClosed) {
                return@synchronized ReadPreparation.Error(
                    IOException("Cronet upload provider is already closed"),
                )
            }

            val remainingForFixedLength = uploadLength?.minus(uploadedBytes)
            if (remainingForFixedLength != null && remainingForFixedLength <= 0L) {
                return@synchronized ReadPreparation.Error(
                    IOException("Cronet requested upload read after fixed-length payload was fully consumed"),
                )
            }

            val source = runCatching {
                activeSource ?: sourceFactory.open().also { activeSource = it }
            }.getOrElse { cause ->
                return@synchronized ReadPreparation.Error(cause)
            }

            val maxBytes = minOf(
                requestedBytes.toLong(),
                remainingForFixedLength ?: requestedBytes.toLong(),
            ).toInt()
            ReadPreparation.Ready(
                source = source,
                maxBytes = maxBytes,
                isChunked = uploadLength == null,
                expectedRemainingBytes = remainingForFixedLength,
            )
        }
    }

    private suspend fun performRead(
        preparedRead: ReadPreparation.Ready,
        uploadDataSink: UploadDataSink,
        targetBuffer: ByteBuffer,
    ) {
        val source = preparedRead.source

        runCatching {
            val originalLimit = targetBuffer.limit()
            targetBuffer.limit(targetBuffer.position() + preparedRead.maxBytes)
            val readCount = try {
                source.channel.readAvailable(targetBuffer)
            } finally {
                targetBuffer.limit(originalLimit)
            }

            if (readCount < 0) {
                if (preparedRead.isChunked) {
                    releaseActiveSource(source, cause = null)
                    uploadDataSink.onReadSucceeded(true)
                    return
                }

                throw IOException(
                    "Upload source ended before declared Content-Length was fully written. " +
                        "remaining=${preparedRead.expectedRemainingBytes}",
                )
            }

            if (readCount == 0) {
                throw IOException("Cronet upload read completed without producing bytes")
            }

            val reachedFixedLengthEnd = synchronized(stateLock) {
                if (activeSource !== source) {
                    false
                } else {
                    uploadedBytes += readCount.toLong()
                    uploadLength?.let { uploadedBytes >= it } ?: false
                }
            }

            uploadDataSink.onReadSucceeded(false)

            if (reachedFixedLengthEnd) {
                releaseActiveSource(source, cause = null)
            }
        }.onFailure { cause ->
            releaseActiveSource(source, cause)
            uploadDataSink.onReadError(cause.asUploadException("Cronet upload read failed"))
        }
    }

    private fun releaseActiveSource(source: UploadSource, cause: Throwable?) {
        val sourceToClose = synchronized(stateLock) {
            if (activeSource === source) {
                activeSource = null
                source
            } else {
                null
            }
        }
        sourceToClose?.close(cause)
    }

    private fun interface UploadSourceFactory {
        fun open(): UploadSource
    }

    private sealed interface RewindMode {
        data object ReopenSource : RewindMode

        data class Unsupported(
            val reason: String,
        ) : RewindMode
    }

    private sealed interface ReadPreparation {
        data class Ready(
            val source: UploadSource,
            val maxBytes: Int,
            val isChunked: Boolean,
            val expectedRemainingBytes: Long?,
        ) : ReadPreparation

        data class Error(
            val cause: Throwable,
        ) : ReadPreparation
    }

    private class UploadSource(
        val channel: ByteReadChannel,
        private val onClose: (Throwable?) -> Unit,
    ) {
        fun close(cause: Throwable?) {
            onClose(cause)
        }
    }

    companion object {
        private const val CHUNKED_UPLOAD_LENGTH: Long = -1L

        fun fromReadChannelContent(
            content: OutgoingContent.ReadChannelContent,
        ): StreamingUploadDataProvider {
            return StreamingUploadDataProvider(
                uploadLength = content.contentLength,
                sourceFactory = UploadSourceFactory {
                    val channel = content.readFrom()
                    UploadSource(
                        channel = channel,
                        onClose = { cause -> channel.cancel(cause) },
                    )
                },
                rewindMode = RewindMode.ReopenSource,
            )
        }

        fun fromWriteChannelContent(
            content: OutgoingContent.WriteChannelContent,
            callContext: CoroutineContext,
        ): StreamingUploadDataProvider {
            return StreamingUploadDataProvider(
                uploadLength = content.contentLength,
                sourceFactory = {
                    val channel = ByteChannel(autoFlush = true)
                    val producerScope = CoroutineScope(
                        callContext + SupervisorJob(callContext[Job]) + CoroutineName("cronet-upload-producer"),
                    )
                    val producer = producerScope.launch {
                        runCatching { content.writeTo(channel) }
                            .onSuccess { channel.close() }
                            .onFailure { channel.close() }
                    }
                    producer.invokeOnCompletion { cause ->
                        if (cause != null) {
                            channel.close()
                        }
                    }

                    UploadSource(
                        channel = channel,
                        onClose = { cause ->
                            channel.cancel(cause)
                            producer.cancel()
                            producerScope.cancel()
                        },
                    )
                },
                rewindMode = RewindMode.ReopenSource,
            )
        }

        fun unsupported(
            content: OutgoingContent,
            reason: String,
        ): StreamingUploadDataProvider {
            return StreamingUploadDataProvider(
                uploadLength = content.contentLength,
                sourceFactory = {
                    throw UnsupportedOperationException(reason)
                },
                rewindMode = RewindMode.Unsupported(reason),
            )
        }
    }
}

private fun Throwable.asUploadException(message: String): Exception {
    return this as? Exception ?: IOException(message, this)
}
