package com.connor.kwitter.domain.post.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatePostRequest(
    val content: String,
    val mediaUrls: List<PostMedia> = emptyList(),
    val parentId: Long? = null
)

