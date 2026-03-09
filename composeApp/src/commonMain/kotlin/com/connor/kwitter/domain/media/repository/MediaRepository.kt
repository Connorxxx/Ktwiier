package com.connor.kwitter.domain.media.repository

import arrow.core.raise.context.Raise
import com.connor.kwitter.domain.media.model.MediaError
import com.connor.kwitter.domain.media.model.MediaUploadResult

interface MediaRepository {
    context(_: Raise<MediaError>)
    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): MediaUploadResult
}
