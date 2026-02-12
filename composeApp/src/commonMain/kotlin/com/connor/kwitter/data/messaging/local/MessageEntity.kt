package com.connor.kwitter.data.messaging.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val imageUrl: String?,
    val readAt: Long?,
    val createdAt: Long,
    val orderIndex: Int
)
