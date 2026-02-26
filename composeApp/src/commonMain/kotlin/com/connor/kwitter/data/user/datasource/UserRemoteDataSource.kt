package com.connor.kwitter.data.user.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserList
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UserStats as DomainUserStats
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
        userId: Long
    ): Either<UserError, UserProfile> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId"))
            handleResponse(response) {
                it.body<UserProfileResponseDto>().toDomain()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun updateCurrentUserProfile(
        request: UpdateProfileRequest
    ): Either<UserError, UserProfile> = either {
        try {
            val response: HttpResponse = httpClient.patch(endpoint("$USERS_PATH/me")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val updatedUser = handleResponse(response) { it.body<UserDto>() }
            getUserProfile(updatedUser.id).bind()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Either<UserError, String> = either {
        try {
            val response: HttpResponse = httpClient.submitFormWithBinaryData(
                url = endpoint("$USERS_PATH/me/avatar"),
                formData = formData {
                    append("avatar", bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, mimeType)
                    })
                }
            )
            handleResponse(response) { it.body<AvatarUploadResponseDto>().avatarUrl }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Avatar upload failed: ${e.message}"))
        }
    }

    suspend fun followUser(
        userId: Long
    ): Either<UserError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.post(endpoint("$USERS_PATH/$userId/follow"))
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun unfollowUser(
        userId: Long
    ): Either<UserError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.delete(endpoint("$USERS_PATH/$userId/follow"))
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserPosts(
        userId: Long,
        query: PostPageQuery
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/posts")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserReplies(
        userId: Long,
        query: PostPageQuery
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/replies")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserLikes(
        userId: Long,
        query: PostPageQuery
    ): Either<UserError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/likes")) {
                parameter("limit", query.limit)
                parameter("offset", query.offset)
            }
            handleResponse(response) { it.body() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserFollowing(
        userId: Long,
        limit: Int,
        offset: Int
    ): Either<UserError, UserList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/following")) {
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) { it.body<UserListResponseDto>().toDomain() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(UserError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getUserFollowers(
        userId: Long,
        limit: Int,
        offset: Int
    ): Either<UserError, UserList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$USERS_PATH/$userId/followers")) {
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) { it.body<UserListResponseDto>().toDomain() }
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
    val id: Long,
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

@Serializable
private data class AvatarUploadResponseDto(
    val avatarUrl: String
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

@Serializable
private data class UserListItemDto(
    val user: UserDto,
    val isFollowedByCurrentUser: Boolean? = null
)

@Serializable
private data class UserListResponseDto(
    val users: List<UserListItemDto>,
    val hasMore: Boolean = false
)

private fun UserListResponseDto.toDomain(): UserList {
    return UserList(
        users = users.map { item ->
            UserListItem(
                id = item.user.id,
                username = item.user.username,
                displayName = item.user.displayName,
                bio = item.user.bio,
                avatarUrl = item.user.avatarUrl,
                isFollowedByCurrentUser = item.isFollowedByCurrentUser
            )
        },
        hasMore = hasMore
    )
}
