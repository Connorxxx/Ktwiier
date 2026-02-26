package com.connor.kwitter.domain.post.repository

import androidx.paging.PagingData
import arrow.core.Either
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.PostMutationEvent
import com.connor.kwitter.domain.post.model.PostStats
import kotlinx.coroutines.flow.Flow

interface PostRepository {
    val timelinePaging: Flow<PagingData<Post>>
    val postMutations: Flow<PostMutationEvent>
    suspend fun getTimeline(query: PostPageQuery = PostPageQuery()): Either<PostError, PostList>
    suspend fun getPost(postId: Long): Either<PostError, Post>
    suspend fun getReplies(
        postId: Long,
        query: PostPageQuery = PostPageQuery()
    ): Either<PostError, PostList>
    suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery = PostPageQuery()
    ): Either<PostError, PostList>
    suspend fun createPost(request: CreatePostRequest): Either<PostError, Post>
    suspend fun likePost(postId: Long): Either<PostError, PostStats>
    suspend fun unlikePost(postId: Long): Either<PostError, PostStats>
    suspend fun bookmarkPost(postId: Long): Either<PostError, Unit>
    suspend fun unbookmarkPost(postId: Long): Either<PostError, Unit>
    suspend fun updateLocalLikeState(postId: Long, isLiked: Boolean, likeCount: Int)
    suspend fun updateLocalBookmarkState(postId: Long, isBookmarked: Boolean)
}

