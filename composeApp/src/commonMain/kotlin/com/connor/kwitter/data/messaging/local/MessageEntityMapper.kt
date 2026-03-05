package com.connor.kwitter.data.messaging.local

import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageSearchItem

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    imageUrl = imageUrl,
    readAt = readAt,
    createdAt = createdAt,
    replyToMessageId = replyToMessageId,
    deletedAt = deletedAt,
    recalledAt = recalledAt
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    imageUrl = imageUrl,
    readAt = readAt,
    createdAt = createdAt,
    replyToMessageId = replyToMessageId,
    deletedAt = deletedAt,
    recalledAt = recalledAt
)

fun MessageSearchResultEntity.toDomain(): MessageSearchItem = MessageSearchItem(
    message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        content = content,
        imageUrl = null,
        readAt = null,
        createdAt = createdAt
    ),
    highlightedContent = highlightedContent
)
