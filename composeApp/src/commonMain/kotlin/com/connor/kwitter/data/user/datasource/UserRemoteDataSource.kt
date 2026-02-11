package com.connor.kwitter.data.user.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UserStats as DomainUserStats
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
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
import kotlinx.serialization.Serializable

class UserRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val USERS_PATH = "/v1/users"
    }

    suspend fun getUserProfile(
        userId: String,
        token: String? = null
    ): Either<UserError, UserProfile> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId")) {
                token?.let { bearerAuth(it) }
            }
            handleResponse(response) {
                it.body<UserProfileResponseDto>().toDomain()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest,
        token: String
    ): Either<UserError, UserProfile> = either {
        try {
            val response: HttpResponse = httpClient.patch(endpoint("$USERS_PATH/me")) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val updatedUser = handleResponse(response) { it.body<UserDto>() }
            getUserProfile(updatedUser.id, token).bind()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun followUser(
        userId: String,
        token: String
    ): Either<UserError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint("$USERS_PATH/$userId/follow")) {
                bearerAuth(token)
            }
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun unfollowUser(
        userId: String,
        token: String
    ): Either<UserError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.delete(endpoint("$USERS_PATH/$userId/follow")) {
                bearerAuth(token)
            }
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserPosts(
        userId: String,
        query: PostPageQuery,
        token: String? = null
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/posts")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
                token?.let { bearerAuth(it) }
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserReplies(
        userId: String,
        query: PostPageQuery,
        token: String? = null
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/replies")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
                token?.let { bearerAuth(it) }
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserLikes(
        userId: String,
        query: PostPageQuery,
        token: String? = null
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/likes")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
                token?.let { bearerAuth(it) }
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    private suspend fun <T> arrow.core.raise.Raise<UserError>.handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                UserError.Unauthorized("Authentication required")
            )
            response.status.value == 404 -> raise(
                UserError.NotFound("User not found")
            )
            response.status.value in 400..499 -> raise(
                UserError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                UserError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                UserError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}

@Serializable
private data class UserProfileResponseDto(
    val user: UserDto,
    val stats: UserStatsDto,
    val isFollowedByCurrentUser: Boolean? = null
)

@Serializable
private data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val createdAt: Long
)

@Serializable
private data class UserStatsDto(
    val followingCount: Int,
    val followersCount: Int,
    val postsCount: Int
)

private fun UserProfileResponseDto.toDomain(): UserProfile {
    return UserProfile(
        id = user.id,
        username = user.username,
        displayName = user.displayName,
        bio = user.bio,
        avatarUrl = user.avatarUrl,
        createdAt = user.createdAt,
        stats = DomainUserStats(
            followingCount = stats.followingCount,
            followersCount = stats.followersCount,
            postsCount = stats.postsCount
        ),
        isFollowedByCurrentUser = isFollowedByCurrentUser
    )
}
