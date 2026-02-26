package com.connor.kwitter.domain.messaging.model

data class NewMessageEvent(
    val messageId: Long,
    val conversationId: Long,
    val senderDisplayName: String,
    val senderUsername: String,
    val contentPreview: String,
    val timestamp: Long
)

data class MessagesReadEvent(
    val conversationId: Long,
    val readByUserId: Long,
    val timestamp: Long
)

