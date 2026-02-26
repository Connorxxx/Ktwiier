package com.connor.kwitter.domain.user.model

import kotlinx.serialization.Serializable

@Serializable
data class UserListItem(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val isFollowedByCurrentUser: Boolean?
)

@Serializable
data class UserList(
    val users: List<UserListItem>,
    val hasMore: Boolean
)
