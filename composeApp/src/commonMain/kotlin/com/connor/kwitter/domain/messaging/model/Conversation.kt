package com.connor.kwitter.domain.messaging.model

data class ConversationUser(
    val id: Long,
    val displayName: String,
    val username: String,
    val avatarUrl: String?
)

data class Conversation(
    val id: Long,
    val otherUser: ConversationUser,
    val lastMessage: Message?,
    val unreadCount: Int,
    val createdAt: Long
)

data class ConversationList(
    val conversations: List<Conversation>,
    val hasMore: Boolean,
    val nextCursor: Long? = null
)

