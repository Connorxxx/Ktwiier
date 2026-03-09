package com.connor.kwitter.domain.post.repository

import androidx.paging.PagingData
import arrow.core.raise.context.Raise
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

    context(_: Raise<PostError>)
    suspend fun getTimeline(query: PostPageQuery = PostPageQuery()): PostList

    context(_: Raise<PostError>)
    suspend fun getPost(postId: Long): Post

    context(_: Raise<PostError>)
    suspend fun getReplies(
        postId: Long,
        query: PostPageQuery = PostPageQuery()
    ): PostList

    context(_: Raise<PostError>)
    suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery = PostPageQuery()
    ): PostList

    context(_: Raise<PostError>)
    suspend fun createPost(request: CreatePostRequest): Post

    context(_: Raise<PostError>)
    suspend fun likePost(postId: Long): PostStats

    context(_: Raise<PostError>)
    suspend fun unlikePost(postId: Long): PostStats

    context(_: Raise<PostError>)
    suspend fun bookmarkPost(postId: Long)

    context(_: Raise<PostError>)
    suspend fun unbookmarkPost(postId: Long)

    suspend fun updateLocalLikeState(postId: Long, isLiked: Boolean, likeCount: Int)
    suspend fun updateLocalBookmarkState(postId: Long, isBookmarked: Boolean)
}

