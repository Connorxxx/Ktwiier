package com.connor.kwitter.data.user.repository

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.user.datasource.UserRemoteDataSource
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.flow.first

class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource,
    private val tokenDataSource: TokenDataSource
) : UserRepository {

    private suspend fun getToken(): String? = tokenDataSource.token.first()?.token

    private suspend fun requireToken(): Either<UserError, String> = either {
        tokenDataSource.token.first()?.token
            ?: raise(UserError.Unauthorized("Not authenticated"))
    }

    override suspend fun getUserProfile(userId: String): Either<UserError, UserProfile> {
        return remoteDataSource.getUserProfile(userId, token = getToken())
    }

    override suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest
    ): Either<UserError, UserProfile> = either {
        val token = requireToken().bind()
        remoteDataSource.updateCurrentUserProfile(request, token).bind()
    }

    override suspend fun followUser(userId: String): Either<UserError, Unit> = either {
        val token = requireToken().bind()
        remoteDataSource.followUser(userId, token).bind()
    }

    override suspend fun unfollowUser(userId: String): Either<UserError, Unit> = either {
        val token = requireToken().bind()
        remoteDataSource.unfollowUser(userId, token).bind()
    }

    override suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserPosts(userId, query, token = getToken())
    }

    override suspend fun getUserReplies(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserReplies(userId, query, token = getToken())
    }

    override suspend fun getUserLikes(
        userId: String,
        query: PostPageQuery
    ): Either<UserError, PostList> {
        return remoteDataSource.getUserLikes(userId, query, token = getToken())
    }
}
