package com.connor.kwitter.data.messaging.local

data class MessageSearchResultEntity(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    val content: String,
    val createdAt: Long,
    val highlightedContent: String
)


