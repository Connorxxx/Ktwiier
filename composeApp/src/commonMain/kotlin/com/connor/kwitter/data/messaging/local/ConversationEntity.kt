package com.connor.kwitter.data.messaging.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: Long,
    val otherUserId: Long,
    val otherUserDisplayName: String,
    val otherUserUsername: String,
    val otherUserAvatarUrl: String?,
    val lastMessageId: Long?,
    val lastMessageContent: String?,
    val lastMessageSenderId: Long?,
    val lastMessageReadAt: Long?,
    val lastMessageCreatedAt: Long?,
    val lastMessageDeletedAt: Long? = null,
    val lastMessageRecalledAt: Long? = null,
    val unreadCount: Int,
    val createdAt: Long,
    val orderIndex: Int
)

