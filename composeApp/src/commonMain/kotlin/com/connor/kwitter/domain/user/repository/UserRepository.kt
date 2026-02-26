package com.connor.kwitter.domain.user.repository

import androidx.paging.PagingData
import arrow.core.Either
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserProfile(userId: Long): Either<UserError, UserProfile>
    suspend fun updateCurrentUserProfile(request: UpdateProfileRequest): Either<UserError, UserProfile>
    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<UserError, String>
    suspend fun followUser(userId: Long): Either<UserError, Unit>
    suspend fun unfollowUser(userId: Long): Either<UserError, Unit>

    fun userPostsPaging(userId: Long): Flow<PagingData<Post>>
    fun userRepliesPaging(userId: Long): Flow<PagingData<Post>>
    fun userLikesPaging(userId: Long): Flow<PagingData<Post>>
    fun userFollowingPaging(userId: Long): Flow<PagingData<UserListItem>>
    fun userFollowersPaging(userId: Long): Flow<PagingData<UserListItem>>
}
