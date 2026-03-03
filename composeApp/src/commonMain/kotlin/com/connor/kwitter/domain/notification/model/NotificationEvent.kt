package com.connor.kwitter.domain.notification.model

import kotlinx.serialization.Serializable

sealed interface NotificationEvent {

    sealed interface PostLikeChanged : NotificationEvent {
        val postId: Long
        val newLikeCount: Int
        val isLiked: Boolean
    }

    @Serializable
    data class NewPostCreated(
        val postId: Long,
        val authorId: Long,
        val authorDisplayName: String,
        val authorUsername: String,
        val content: String,
        val createdAt: Long
    ) : NotificationEvent

    @Serializable
    data class PostLiked(
        override val postId: Long,
        val likedByUserId: Long,
        val likedByDisplayName: String,
        val likedByUsername: String,
        override val newLikeCount: Int,
        override val isLiked: Boolean = true,
        val timestamp: Long
    ) : PostLikeChanged

    @Serializable
    data class PostUnliked(
        override val postId: Long,
        val unlikedByUserId: Long,
        override val newLikeCount: Int,
        override val isLiked: Boolean = false,
        val timestamp: Long
    ) : PostLikeChanged

    @Serializable
    data class NewMessage(
        val messageId: Long,
        val conversationId: Long,
        val senderDisplayName: String,
        val senderUsername: String,
        val contentPreview: String,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class MessagesRead(
        val conversationId: Long,
        val readByUserId: Long,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class MessageRecalled(
        val messageId: Long,
        val conversationId: Long,
        val recalledByUserId: Long,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class TypingIndicator(
        val conversationId: Long,
        val userId: Long,
        val isTyping: Boolean,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class UserPresenceChanged(
        val userId: Long,
        val isOnline: Boolean,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class PresenceSnapshot(
        val users: List<PresenceUser>
    ) : NotificationEvent

    @Serializable
    data class PresenceUser(
        val userId: Long,
        val isOnline: Boolean,
        val timestamp: Long
    )
}
