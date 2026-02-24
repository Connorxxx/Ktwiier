package com.connor.kwitter.data.messaging.local

import com.connor.kwitter.domain.messaging.model.Message

fun Message.toEntity(orderIndex: Int): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    imageUrl = imageUrl,
    readAt = readAt,
    createdAt = createdAt,
    orderIndex = orderIndex,
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
