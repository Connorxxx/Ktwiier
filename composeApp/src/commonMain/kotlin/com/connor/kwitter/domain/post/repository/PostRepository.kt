package com.connor.kwitter.domain.post.repository

import arrow.core.Either
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.MediaUploadResponse

interface PostRepository {
    suspend fun getTimeline(query: PostPageQuery = PostPageQuery()): Either<PostError, PostList>
    suspend fun getPost(postId: String): Either<PostError, Post>
    suspend fun getReplies(
        postId: String,
        query: PostPageQuery = PostPageQuery()
    ): Either<PostError, PostList>
    suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery = PostPageQuery()
    ): Either<PostError, PostList>
    suspend fun createPost(request: CreatePostRequest): Either<PostError, Post>
    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<PostError, MediaUploadResponse>
}
