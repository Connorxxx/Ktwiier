package com.connor.kwitter.data.post.repository

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.post.datasource.PostRemoteDataSource
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.MediaUploadResponse
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.flow.first

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val tokenDataSource: TokenDataSource
) : PostRepository {

    private suspend fun getToken(): String? = tokenDataSource.token.first()?.token

    override suspend fun getTimeline(query: PostPageQuery): Either<PostError, PostList> {
        return remoteDataSource.getTimeline(query, token = getToken())
    }

    override suspend fun getPost(postId: String): Either<PostError, Post> {
        return remoteDataSource.getPost(postId, token = getToken())
    }

    override suspend fun getReplies(
        postId: String,
        query: PostPageQuery
    ): Either<PostError, PostList> {
        return remoteDataSource.getReplies(postId, query, token = getToken())
    }

    override suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery
    ): Either<PostError, PostList> {
        return remoteDataSource.getUserPosts(userId, query)
    }

    override suspend fun createPost(request: CreatePostRequest): Either<PostError, Post> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.createPost(
            token = token.token,
            request = request
        ).bind()
    }

    override suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<PostError, MediaUploadResponse> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.uploadMedia(
            token = token.token,
            bytes = bytes,
            fileName = fileName,
            mimeType = mimeType
        ).bind()
    }

    override suspend fun likePost(postId: String): Either<PostError, PostStats> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.likePost(token.token, postId).bind()
    }

    override suspend fun unlikePost(postId: String): Either<PostError, PostStats> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.unlikePost(token.token, postId).bind()
    }

    override suspend fun bookmarkPost(postId: String): Either<PostError, Unit> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.bookmarkPost(token.token, postId).bind()
    }

    override suspend fun unbookmarkPost(postId: String): Either<PostError, Unit> = either {
        val token = tokenDataSource.token.first()
            ?: raise(PostError.Unauthorized("Not authenticated"))
        remoteDataSource.unbookmarkPost(token.token, postId).bind()
    }
}
