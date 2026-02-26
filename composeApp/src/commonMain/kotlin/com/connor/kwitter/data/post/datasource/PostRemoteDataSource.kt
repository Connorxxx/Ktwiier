package com.connor.kwitter.data.post.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.LikeResponse
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.PostStats
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class PostRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val TIMELINE_PATH = "/v1/posts/timeline"
        const val POSTS_PATH = "/v1/posts"
    }

    suspend fun getTimeline(
        query: PostPageQuery
    ): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint(TIMELINE_PATH)) {
                parameter("limit", query.limit)
                if (query.beforeId != null) parameter("beforeId", query.beforeId) else parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getPost(postId: Long): Either<PostError, Post> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId"))
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getReplies(
        postId: Long,
        query: PostPageQuery
    ): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId/replies")) {
                parameter("limit", query.limit)
                if (query.beforeId != null) parameter("afterId", query.beforeId) else parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery
    ): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/users/$userId")) {
                parameter("limit", query.limit)
                if (query.beforeId != null) parameter("beforeId", query.beforeId) else parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun createPost(
        request: CreatePostRequest
    ): Either<PostError, Post> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint(POSTS_PATH)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun likePost(postId: Long): Either<PostError, PostStats> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint("$POSTS_PATH/$postId/like"))
            handleResponse<LikeResponse>(response) { it.body() }.stats
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun unlikePost(postId: Long): Either<PostError, PostStats> = either {
        try {
            val response: HttpResponse = httpClient.delete(endpoint("$POSTS_PATH/$postId/like"))
            handleResponse<LikeResponse>(response) { it.body() }.stats
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun bookmarkPost(postId: Long): Either<PostError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint("$POSTS_PATH/$postId/bookmark"))
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun unbookmarkPost(postId: Long): Either<PostError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.delete(endpoint("$POSTS_PATH/$postId/bookmark"))
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    private suspend fun <T> arrow.core.raise.Raise<PostError>.handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                PostError.Unauthorized("Authentication required")
            )
            response.status.value == 404 -> raise(
                PostError.NotFound("Resource not found")
            )
            response.status.value in 400..499 -> raise(
                PostError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                PostError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                PostError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}

