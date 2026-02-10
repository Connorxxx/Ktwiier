package com.connor.kwitter.data.post.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val mediaJson: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val authorId: String,
    val authorDisplayName: String,
    val authorAvatarUrl: String?,
    val replyCount: Int,
    val likeCount: Int,
    val viewCount: Int,
    val parentPostJson: String?,
    val isLikedByCurrentUser: Boolean?,
    val isBookmarkedByCurrentUser: Boolean?,
    val timelineIndex: Int
)
