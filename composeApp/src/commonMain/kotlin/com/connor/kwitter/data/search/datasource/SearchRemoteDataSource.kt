package com.connor.kwitter.data.search.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.search.model.SearchError
import com.connor.kwitter.domain.user.model.UserList
import com.connor.kwitter.domain.user.model.UserListItem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

class SearchRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val SEARCH_PATH = "/v1/search"
    }

    suspend fun searchPosts(
        query: String,
        sort: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$SEARCH_PATH/posts")) {
                parameter("q", query)
                parameter("sort", sort)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) {
                val dto = it.body<SearchPostsResponseDto>()
                PostList(
                    posts = dto.posts,
                    hasMore = dto.hasMore,
                    total = dto.total
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(SearchError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun searchReplies(
        query: String,
        sort: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, PostList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$SEARCH_PATH/replies")) {
                parameter("q", query)
                parameter("sort", sort)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) {
                val dto = it.body<SearchPostsResponseDto>()
                PostList(
                    posts = dto.posts,
                    hasMore = dto.hasMore,
                    total = dto.total
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(SearchError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun searchUsers(
        query: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, UserList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint("$SEARCH_PATH/users")) {
                parameter("q", query)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) {
                val dto = it.body<SearchUsersResponseDto>()
                UserList(
                    users = dto.users.map { item ->
                        UserListItem(
                            id = item.user.id,
                            username = item.user.username,
                            displayName = item.user.displayName,
                            bio = item.user.bio,
                            avatarUrl = item.user.avatarUrl,
                            isFollowedByCurrentUser = item.isFollowedByCurrentUser
                        )
                    },
                    hasMore = dto.hasMore
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(SearchError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    private suspend fun <T> arrow.core.raise.Raise<SearchError>.handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                SearchError.Unauthorized("Authentication required")
            )
            response.status.value == 404 -> raise(
                SearchError.NotFound("Not found")
            )
            response.status.value in 400..499 -> raise(
                SearchError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                SearchError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                SearchError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}

@Serializable
private data class SearchPostsResponseDto(
    val posts: List<Post>,
    val hasMore: Boolean,
    val total: Int? = null
)

@Serializable
private data class SearchUserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val createdAt: Long
)

@Serializable
private data class SearchUserItemDto(
    val user: SearchUserDto,
    val isFollowedByCurrentUser: Boolean? = null
)

@Serializable
private data class SearchUsersResponseDto(
    val users: List<SearchUserItemDto>,
    val hasMore: Boolean,
    val total: Int? = null
)
