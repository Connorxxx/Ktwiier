package com.connor.kwitter.domain.messaging.model

data class ConversationUser(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?
)

data class Conversation(
    val id: String,
    val otherUser: ConversationUser,
    val lastMessage: Message?,
    val unreadCount: Int,
    val createdAt: Long
)

data class ConversationList(
    val conversations: List<Conversation>,
    val hasMore: Boolean,
    val nextCursor: String? = null
)
