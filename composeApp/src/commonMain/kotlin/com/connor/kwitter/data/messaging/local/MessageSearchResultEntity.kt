package com.connor.kwitter.data.messaging.local

data class MessageSearchResultEntity(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val createdAt: Long,
    val highlightedContent: String
)
