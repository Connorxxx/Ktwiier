package com.connor.kwitter.data.user.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import arrow.core.raise.context.Raise
import com.connor.kwitter.core.paging.OffsetPagingSource
import com.connor.kwitter.data.user.datasource.UserRemoteDataSource
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {

    private companion object {
        const val PAGE_SIZE = 20
    }

    context(_: Raise<UserError>)
    override suspend fun getUserProfile(userId: Long): UserProfile =
        remoteDataSource.getUserProfile(userId)

    context(_: Raise<UserError>)
    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest
    ): UserProfile = remoteDataSource.updateCurrentUserProfile(request)

    context(_: Raise<UserError>)
    override suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): String = remoteDataSource.uploadAvatar(bytes, fileName, mimeType)

    context(_: Raise<UserError>)
    override suspend fun followUser(userId: Long) =
        remoteDataSource.followUser(userId)

    context(_: Raise<UserError>)
    override suspend fun unfollowUser(userId: Long) =
        remoteDataSource.unfollowUser(userId)

    override fun userPostsPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource<UserError, Post> { limit, offset ->
            remoteDataSource.getUserPosts(userId, PostPageQuery(limit, offset)).let {
                it.posts to it.hasMore
            }
        }
    }.flow

    override fun userRepliesPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource<UserError, Post> { limit, offset ->
            remoteDataSource.getUserReplies(userId, PostPageQuery(limit, offset)).let {
                it.posts to it.hasMore
            }
        }
    }.flow

    override fun userLikesPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource<UserError, Post> { limit, offset ->
            remoteDataSource.getUserLikes(userId, PostPageQuery(limit, offset)).let {
                it.posts to it.hasMore
            }
        }
    }.flow

    override fun userFollowingPaging(userId: Long): Flow<PagingData<UserListItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource<UserError, UserListItem> { limit, offset ->
            remoteDataSource.getUserFollowing(userId, limit, offset).let {
                it.users to it.hasMore
            }
        }
    }.flow

    override fun userFollowersPaging(userId: Long): Flow<PagingData<UserListItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource<UserError, UserListItem> { limit, offset ->
            remoteDataSource.getUserFollowers(userId, limit, offset).let {
                it.users to it.hasMore
            }
        }
    }.flow
}
