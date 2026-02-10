package com.connor.kwitter.domain.user.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val createdAt: Long,
    val stats: UserStats,
    val isFollowedByCurrentUser: Boolean?
)

@Serializable
data class UserStats(
    val followingCount: Int,
    val followersCount: Int,
    val postsCount: Int
)
