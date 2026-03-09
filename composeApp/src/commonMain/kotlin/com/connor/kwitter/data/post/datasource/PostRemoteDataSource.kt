package com.connor.kwitter.data.post.datasource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.catch
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

class PostRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val TIMELINE_PATH = "/v1/posts/timeline"
        const val POSTS_PATH = "/v1/posts"
    }

    context(_: Raise<PostError>)
    suspend fun getTimeline(
        query: PostPageQuery
    ): PostList = catch({
        val response: HttpResponse = httpClient.get(endpoint(TIMELINE_PATH)) {
            parameter("limit", query.limit)
            if (query.beforeId != null) parameter("beforeId", query.beforeId) else parameter("offset", query.offset)
        }
        handleResponse(response) { it.body() }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun getPost(postId: Long): Post = catch({
        val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId"))
        handleResponse(response) { it.body() }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun getReplies(
        postId: Long,
        query: PostPageQuery
    ): PostList = catch({
        val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId/replies")) {
            parameter("limit", query.limit)
            if (query.beforeId != null) parameter("afterId", query.beforeId) else parameter("offset", query.offset)
        }
        handleResponse(response) { it.body() }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery
    ): PostList = catch({
        val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/users/$userId")) {
            parameter("limit", query.limit)
            if (query.beforeId != null) parameter("beforeId", query.beforeId) else parameter("offset", query.offset)
        }
        handleResponse(response) { it.body() }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun createPost(
        request: CreatePostRequest
    ): Post = catch({
        val response: HttpResponse = httpClient.post(endpoint(POSTS_PATH)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        handleResponse(response) { it.body() }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun likePost(postId: Long): PostStats = catch({
        val response: HttpResponse = httpClient.post(endpoint("$POSTS_PATH/$postId/like"))
        handleResponse<LikeResponse>(response) { it.body() }.stats
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun unlikePost(postId: Long): PostStats = catch({
        val response: HttpResponse = httpClient.delete(endpoint("$POSTS_PATH/$postId/like"))
        handleResponse<LikeResponse>(response) { it.body() }.stats
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun bookmarkPost(postId: Long) = catch({
        val response: HttpResponse = httpClient.post(endpoint("$POSTS_PATH/$postId/bookmark"))
        handleResponse(response) { }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    suspend fun unbookmarkPost(postId: Long) = catch({
        val response: HttpResponse = httpClient.delete(endpoint("$POSTS_PATH/$postId/bookmark"))
        handleResponse(response) { }
    }) {
        raise(PostError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<PostError>)
    private suspend fun <T> handleResponse(
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

