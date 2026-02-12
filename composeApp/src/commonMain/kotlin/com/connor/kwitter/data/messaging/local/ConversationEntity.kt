package com.connor.kwitter.data.messaging.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val otherUserId: String,
    val otherUserDisplayName: String,
    val otherUserUsername: String,
    val otherUserAvatarUrl: String?,
    val lastMessageId: String?,
    val lastMessageContent: String?,
    val lastMessageSenderId: String?,
    val lastMessageReadAt: Long?,
    val lastMessageCreatedAt: Long?,
    val unreadCount: Int,
    val createdAt: Long,
    val orderIndex: Int
)
