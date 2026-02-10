package com.connor.kwitter.domain.user.repository

import arrow.core.Either
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile

interface UserRepository {
    suspend fun getUserProfile(userId: String): Either<UserError, UserProfile>
    suspend fun followUser(userId: String): Either<UserError, Unit>
    suspend fun unfollowUser(userId: String): Either<UserError, Unit>
    suspend fun getUserPosts(userId: String, query: PostPageQuery): Either<UserError, PostList>
    suspend fun getUserReplies(userId: String, query: PostPageQuery): Either<UserError, PostList>
    suspend fun getUserLikes(userId: String, query: PostPageQuery): Either<UserError, PostList>
}
