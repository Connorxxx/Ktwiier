package com.connor.kwitter.domain.user.repository

import androidx.paging.PagingData
import arrow.core.raise.context.Raise
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    context(_: Raise<UserError>)
    suspend fun getUserProfile(userId: Long): UserProfile

    context(_: Raise<UserError>)
    suspend fun updateCurrentUserProfile(request: UpdateProfileRequest): UserProfile

    context(_: Raise<UserError>)
    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String

    context(_: Raise<UserError>)
    suspend fun followUser(userId: Long)

    context(_: Raise<UserError>)
    suspend fun unfollowUser(userId: Long)

    fun userPostsPaging(userId: Long): Flow<PagingData<Post>>
    fun userRepliesPaging(userId: Long): Flow<PagingData<Post>>
    fun userLikesPaging(userId: Long): Flow<PagingData<Post>>
    fun userFollowingPaging(userId: Long): Flow<PagingData<UserListItem>>
    fun userFollowersPaging(userId: Long): Flow<PagingData<UserListItem>>
}
