package com.connor.kwitter.data.media.repository

import arrow.core.raise.context.Raise
import com.connor.kwitter.data.media.datasource.MediaRemoteDataSource
import com.connor.kwitter.domain.media.model.MediaError
import com.connor.kwitter.domain.media.model.MediaUploadResult
import com.connor.kwitter.domain.media.repository.MediaRepository

class MediaRepositoryImpl(
    private val remoteDataSource: MediaRemoteDataSource
) : MediaRepository {

    context(_: Raise<MediaError>)
    override suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): MediaUploadResult =
        remoteDataSource.uploadMedia(bytes, fileName, mimeType)
}
