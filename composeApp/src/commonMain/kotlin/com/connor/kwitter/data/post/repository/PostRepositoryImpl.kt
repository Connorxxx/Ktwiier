package com.connor.kwitter.data.post.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import arrow.core.Either
import com.connor.kwitter.data.post.datasource.PostRemoteDataSource
import com.connor.kwitter.data.post.datasource.TimelineRemoteMediator
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.data.post.local.toDomain
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostMutationEvent
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class PostRepositoryImpl(
    private val remoteDataSource: PostRemoteDataSource,
    private val database: AppDatabase
) : PostRepository {

    private val postDao = database.postDao()
    private val timelineRefreshTrigger = MutableStateFlow(0L)
    private val _postMutations = MutableSharedFlow<PostMutationEvent>(extraBufferCapacity = 1)
    override val postMutations: Flow<PostMutationEvent> = _postMutations.asSharedFlow()

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    override val timelinePaging: Flow<PagingData<Post>> = timelineRefreshTrigger.flatMapLatest {
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            remoteMediator = TimelineRemoteMediator(
                remoteDataSource = remoteDataSource,
                database = database
            ),
            pagingSourceFactory = { postDao.getTimelinePagingSource() }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun getTimeline(query: PostPageQuery): Either<PostError, PostList> {
        return remoteDataSource.getTimeline(query)
    }

    override suspend fun getPost(postId: Long): Either<PostError, Post> {
        return remoteDataSource.getPost(postId)
    }

    override suspend fun getReplies(
        postId: Long,
        query: PostPageQuery
    ): Either<PostError, PostList> {
        return remoteDataSource.getReplies(postId, query)
    }

    override suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery
    ): Either<PostError, PostList> {
        return remoteDataSource.getUserPosts(userId, query)
    }

    override suspend fun createPost(request: CreatePostRequest): Either<PostError, Post> {
        return remoteDataSource.createPost(request).onRight { post ->
            timelineRefreshTrigger.update { it + 1 }
            _postMutations.emit(
                PostMutationEvent.PostCreated(
                    postId = post.id,
                    parentId = post.parentId
                )
            )
        }
    }

    override suspend fun likePost(postId: Long): Either<PostError, PostStats> {
        return remoteDataSource.likePost(postId)
    }

    override suspend fun unlikePost(postId: Long): Either<PostError, PostStats> {
        return remoteDataSource.unlikePost(postId)
    }

    override suspend fun bookmarkPost(postId: Long): Either<PostError, Unit> {
        return remoteDataSource.bookmarkPost(postId)
    }

    override suspend fun unbookmarkPost(postId: Long): Either<PostError, Unit> {
        return remoteDataSource.unbookmarkPost(postId)
    }

    override suspend fun updateLocalLikeState(postId: Long, isLiked: Boolean, likeCount: Int) {
        postDao.updateLikeState(postId, isLiked, likeCount)
    }

    override suspend fun updateLocalBookmarkState(postId: Long, isBookmarked: Boolean) {
        postDao.updateBookmarkState(postId, isBookmarked)
    }
}

