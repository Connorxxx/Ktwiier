package com.connor.kwitter.data.post.local

import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.post.model.PostSummary
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Post.toEntity(timelineIndex: Int): PostEntity = PostEntity(
    id = id,
    content = content,
    mediaJson = json.encodeToString(media),
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    authorId = author.id,
    authorDisplayName = author.displayName,
    authorAvatarUrl = author.avatarUrl,
    replyCount = stats.replyCount,
    likeCount = stats.likeCount,
    viewCount = stats.viewCount,
    parentPostJson = parentPost?.let { json.encodeToString(it) },
    isLikedByCurrentUser = isLikedByCurrentUser,
    isBookmarkedByCurrentUser = isBookmarkedByCurrentUser,
    timelineIndex = timelineIndex
)

fun PostEntity.toDomain(): Post = Post(
    id = id,
    content = content,
    media = json.decodeFromString<List<PostMedia>>(mediaJson),
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    author = PostAuthor(
        id = authorId,
        displayName = authorDisplayName,
        avatarUrl = authorAvatarUrl
    ),
    stats = PostStats(
        replyCount = replyCount,
        likeCount = likeCount,
        viewCount = viewCount
    ),
    parentPost = parentPostJson?.let { json.decodeFromString<PostSummary>(it) },
    isLikedByCurrentUser = isLikedByCurrentUser,
    isBookmarkedByCurrentUser = isBookmarkedByCurrentUser
)
