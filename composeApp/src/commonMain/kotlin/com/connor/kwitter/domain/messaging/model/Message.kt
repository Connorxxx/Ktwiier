package com.connor.kwitter.domain.messaging.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val imageUrl: String?,
    val readAt: Long?,
    val createdAt: Long
)

data class MessageList(
    val messages: List<Message>,
    val hasMore: Boolean
)
