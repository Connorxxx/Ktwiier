package com.connor.kwitter.data.user.repository

import arrow.core.Either
import com.connor.kwitter.data.user.datasource.UserRemoteDataSource
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.repository.UserRepository

class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {

    override suspend fun getUserProfile(userId: String): Either<UserError, UserProfile> {
        return remoteDataSource.getUserProfile(userId)
    }

    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest
    ): Either<UserError, UserProfile> {
        return remoteDataSource.updateCurrentUserProfile(request)
    }

    override suspend fun followUser(userId: String): Either<UserError, Unit> {
        return remoteDataSource.followUser(userId)
    }

    override suspend fun unfollowUser(userId: String): Either<UserError, Unit> {
        return remoteDataSource.unfollowUser(userId)
    }

    override suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserPosts(userId, query)
    }

    override suspend fun getUserReplies(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserReplies(userId, query)
    }

    override suspend fun getUserLikes(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserLikes(userId, query)
    }
}
