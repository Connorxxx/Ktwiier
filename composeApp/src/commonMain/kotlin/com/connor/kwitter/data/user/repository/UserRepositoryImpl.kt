package com.connor.kwitter.data.user.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import arrow.core.Either
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

    override suspend fun getUserProfile(userId: Long): Either<UserError, UserProfile> {
        return remoteDataSource.getUserProfile(userId)
    }

    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest
    ): Either<UserError, UserProfile> {
        return remoteDataSource.updateCurrentUserProfile(request)
    }

    override suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<UserError, String> {
        return remoteDataSource.uploadAvatar(bytes, fileName, mimeType)
    }

    override suspend fun followUser(userId: Long): Either<UserError, Unit> {
        return remoteDataSource.followUser(userId)
    }

    override suspend fun unfollowUser(userId: Long): Either<UserError, Unit> {
        return remoteDataSource.unfollowUser(userId)
    }

    override fun userPostsPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.getUserPosts(userId, PostPageQuery(limit, offset))
                .map { it.posts to it.hasMore }
        }
    }.flow

    override fun userRepliesPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.getUserReplies(userId, PostPageQuery(limit, offset))
                .map { it.posts to it.hasMore }
        }
    }.flow

    override fun userLikesPaging(userId: Long): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.getUserLikes(userId, PostPageQuery(limit, offset))
                .map { it.posts to it.hasMore }
        }
    }.flow

    override fun userFollowingPaging(userId: Long): Flow<PagingData<UserListItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.getUserFollowing(userId, limit, offset)
                .map { it.users to it.hasMore }
        }
    }.flow

    override fun userFollowersPaging(userId: Long): Flow<PagingData<UserListItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.getUserFollowers(userId, limit, offset)
                .map { it.users to it.hasMore }
        }
    }.flow
}
