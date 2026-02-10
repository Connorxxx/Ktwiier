package com.connor.kwitter.domain.post.model

sealed interface PostMutationEvent {
    data class PostCreated(
        val postId: String,
        val parentId: String?
    ) : PostMutationEvent
}
