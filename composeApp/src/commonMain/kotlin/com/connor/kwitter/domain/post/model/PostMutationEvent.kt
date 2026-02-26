package com.connor.kwitter.domain.post.model

sealed interface PostMutationEvent {
    data class PostCreated(
        val postId: Long,
        val parentId: Long?
    ) : PostMutationEvent
}

