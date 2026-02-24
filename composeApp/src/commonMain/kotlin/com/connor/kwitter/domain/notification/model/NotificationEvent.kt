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

    @Serializable
    data class NewMessage(
        val messageId: String,
        val conversationId: String,
        val senderDisplayName: String,
        val senderUsername: String,
        val contentPreview: String,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class MessagesRead(
        val conversationId: String,
        val readByUserId: String,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class MessageRecalled(
        val messageId: String,
        val conversationId: String,
        val recalledByUserId: String,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class TypingIndicator(
        val conversationId: String,
        val userId: String,
        val isTyping: Boolean,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class UserPresenceChanged(
        val userId: String,
        val isOnline: Boolean,
        val timestamp: Long
    ) : NotificationEvent

    @Serializable
    data class PresenceSnapshot(
        val users: List<PresenceUser>
    ) : NotificationEvent

    @Serializable
    data class PresenceUser(
        val userId: String,
        val isOnline: Boolean,
        val timestamp: Long
    )
}
