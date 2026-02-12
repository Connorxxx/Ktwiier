package com.connor.kwitter.domain.notification.model

import kotlinx.serialization.Serializable

sealed interface NotificationEvent {

    @Serializable
    data class NewPostCreated(
        val postId: String,
        val authorId: String,
        val authorDisplayName: String,
        val authorUsername: String,
        val content: String,
        val createdAt: Long
    ) : NotificationEvent

    @Serializable
    data class PostLiked(
        val postId: String,
        val likedByUserId: String,
        val likedByDisplayName: String,
        val likedByUsername: String,
        val newLikeCount: Int,
        val timestamp: Long
    ) : NotificationEvent
}
