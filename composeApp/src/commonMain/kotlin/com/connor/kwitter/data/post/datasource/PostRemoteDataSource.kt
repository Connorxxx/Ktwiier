package com.connor.kwitter.data.post.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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

    suspend fun getTimeline(query: PostPageQuery): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint(TIMELINE_PATH)) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getPost(postId: String): Either<PostError, Post> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId"))
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getReplies(
        postId: String,
        query: PostPageQuery
    ): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/$postId/replies")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery
    ): Either<PostError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$POSTS_PATH/users/$userId")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(PostError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun createPost(
        token: String,
        request: CreatePostRequest
    ): Either<PostError, Post> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint(POSTS_PATH)) {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(request)
            }
            handleResponse(response) { it.body() }
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
