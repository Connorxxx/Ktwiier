package com.connor.kwitter.data.post.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.post.datasource.PostRemoteDataSource
import com.connor.kwitter.data.post.datasource.TimelineRemoteMediator
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.data.post.local.toDomain
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.MediaUploadResponse
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val tokenDataSource: TokenDataSource,
    private val database: AppDatabase
) : PostRepository {

    private val postDao = database.postDao()

    private suspend fun getToken(): String? = tokenDataSource.token.first()?.token

    @OptIn(ExperimentalPagingApi::class)
    override val timelinePaging: Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        remoteMediator = TimelineRemoteMediator(
            remoteDataSource = remoteDataSource,
            database = database,
            getToken = { getToken() }
        ),
        pagingSourceFactory = { postDao.getTimelinePagingSource() }
    ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

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
        val post = remoteDataSource.createPost(
            token = token.token,
            request = request
        ).bind()
        postDao.clearAll()
        database.remoteKeyDao().deleteByLabel("timeline")
        post
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

    override suspend fun updateLocalLikeState(postId: String, isLiked: Boolean, likeCount: Int) {
        postDao.updateLikeState(postId, isLiked, likeCount)
    }

    override suspend fun updateLocalBookmarkState(postId: String, isBookmarked: Boolean) {
        postDao.updateBookmarkState(postId, isBookmarked)
    }
}
