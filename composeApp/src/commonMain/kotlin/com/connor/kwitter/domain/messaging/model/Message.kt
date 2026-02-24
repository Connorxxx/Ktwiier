package com.connor.kwitter.domain.messaging.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val imageUrl: String?,
    val readAt: Long?,
    val createdAt: Long,
    val replyToMessageId: String? = null,
    val deletedAt: Long? = null,
    val recalledAt: Long? = null
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isRecalled: Boolean get() = recalledAt != null
    val isNormalMessage: Boolean get() = !isDeleted && !isRecalled
}

data class MessageList(
    val messages: List<Message>,
    val hasMore: Boolean
)
