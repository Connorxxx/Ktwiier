package com.connor.kwitter.domain.messaging.model

data class NewMessageEvent(
    val messageId: String,
    val conversationId: String,
    val senderDisplayName: String,
    val senderUsername: String,
    val contentPreview: String,
    val timestamp: Long
)

data class MessagesReadEvent(
    val conversationId: String,
    val readByUserId: String,
    val timestamp: Long
)
