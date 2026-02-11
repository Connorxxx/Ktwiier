package com.connor.kwitter.domain.media.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaUploadResult(
    val url: String,
    val type: String
)
