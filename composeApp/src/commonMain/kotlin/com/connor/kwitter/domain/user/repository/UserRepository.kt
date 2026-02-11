package com.connor.kwitter.domain.user.repository

import arrow.core.Either
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UpdateProfileRequest

interface UserRepository {
    suspend fun getUserProfile(userId: String): Either<UserError, UserProfile>
    suspend fun updateCurrentUserProfile(request: UpdateProfileRequest): Either<UserError, UserProfile>
    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<UserError, String>
    suspend fun followUser(userId: String): Either<UserError, Unit>
    suspend fun unfollowUser(userId: String): Either<UserError, Unit>
    suspend fun getUserPosts(userId: String, query: PostPageQuery): Either<UserError, PostList>
    suspend fun getUserReplies(userId: String, query: PostPageQuery): Either<UserError, PostList>
    suspend fun getUserLikes(userId: String, query: PostPageQuery): Either<UserError, PostList>
}
