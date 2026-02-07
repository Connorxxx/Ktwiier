package com.connor.kwitter.domain.post.model

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,
    val content: String,
    val media: List<PostMedia> = emptyList(),
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val author: PostAuthor,
    val stats: PostStats,
    val parentPost: PostSummary? = null
) {
    val authorName: String
        get() = author.displayName

    val replyCount: Int
        get() = stats.replyCount
}

@Serializable
data class PostMedia(
    val url: String,
    val type: PostMediaType
)

@Serializable
enum class PostMediaType {
    IMAGE,
    VIDEO
}

@Serializable
data class PostAuthor(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null
)

@Serializable
data class PostStats(
    val replyCount: Int,
    val likeCount: Int,
    val viewCount: Int
)

@Serializable
data class PostSummary(
    val id: String,
    val content: String,
    val author: PostAuthor,
    val createdAt: Long
)

@Serializable
data class PostList(
    val posts: List<Post>,
    val hasMore: Boolean,
    val total: Int? = null
)

data class PostPageQuery(
    val limit: Int = 20,
    val offset: Int = 0
) {
    init {
        require(limit > 0) { "limit must be > 0" }
        require(offset >= 0) { "offset must be >= 0" }
    }
}
