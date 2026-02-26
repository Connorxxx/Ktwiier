package com.connor.kwitter.data.messaging.local

import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.ConversationUser
import com.connor.kwitter.domain.messaging.model.Message

fun Conversation.toEntity(orderIndex: Int): ConversationEntity = ConversationEntity(
    id = id,
    otherUserId = otherUser.id,
    otherUserDisplayName = otherUser.displayName,
    otherUserUsername = otherUser.username,
    otherUserAvatarUrl = otherUser.avatarUrl,
    lastMessageId = lastMessage?.id,
    lastMessageContent = lastMessage?.content,
    lastMessageSenderId = lastMessage?.senderId,
    lastMessageReadAt = lastMessage?.readAt,
    lastMessageCreatedAt = lastMessage?.createdAt,
    lastMessageDeletedAt = lastMessage?.deletedAt,
    lastMessageRecalledAt = lastMessage?.recalledAt,
    unreadCount = unreadCount,
    createdAt = createdAt,
    orderIndex = orderIndex
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    otherUser = ConversationUser(
        id = otherUserId,
        displayName = otherUserDisplayName,
        username = otherUserUsername,
        avatarUrl = otherUserAvatarUrl
    ),
    lastMessage = if (lastMessageId != null && lastMessageContent != null && lastMessageCreatedAt != null) {
        Message(
            id = lastMessageId,
            conversationId = id,
            senderId = lastMessageSenderId ?: 0L,
            content = lastMessageContent,
            imageUrl = null,
            readAt = lastMessageReadAt,
            createdAt = lastMessageCreatedAt,
            deletedAt = lastMessageDeletedAt,
            recalledAt = lastMessageRecalledAt
        )
    } else null,
    unreadCount = unreadCount,
    createdAt = createdAt
)


