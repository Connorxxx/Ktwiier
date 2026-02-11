package com.connor.kwitter.domain.media.repository

import arrow.core.Either
import com.connor.kwitter.domain.media.model.MediaError
import com.connor.kwitter.domain.media.model.MediaUploadResult

interface MediaRepository {
    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<MediaError, MediaUploadResult>
}
