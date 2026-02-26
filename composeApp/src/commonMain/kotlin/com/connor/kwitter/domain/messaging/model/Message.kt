package com.connor.kwitter.domain.messaging.model

data class Message(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val content: String,
    val imageUrl: String?,
    val readAt: Long?,
    val createdAt: Long,
    val replyToMessageId: Long? = null,
    val deletedAt: Long? = null,
    val recalledAt: Long? = null
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isRecalled: Boolean get() = recalledAt != null
    val isNormalMessage: Boolean get() = !isDeleted && !isRecalled
}

data class MessageList(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextCursor: Long? = null
)


