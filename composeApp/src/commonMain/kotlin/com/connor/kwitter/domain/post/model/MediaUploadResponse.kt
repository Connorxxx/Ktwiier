package com.connor.kwitter.domain.post.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaUploadResponse(
    val url: String,
    val type: String
)
