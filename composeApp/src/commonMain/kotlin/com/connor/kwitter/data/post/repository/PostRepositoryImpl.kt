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
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.flow.first

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val tokenDataSource: TokenDataSource
) : PostRepository {

    override suspend fun getTimeline(query: PostPageQuery): Either<PostError, PostList> {
        return remoteDataSource.getTimeline(query)
    }

    override suspend fun getPost(postId: String): Either<PostError, Post> {
        return remoteDataSource.getPost(postId)
    }

    override suspend fun getReplies(
        postId: String,
        query: PostPageQuery
    ): Either<PostError, PostList> {
        return remoteDataSource.getReplies(postId, query)
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
}
