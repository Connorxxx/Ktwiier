package com.connor.kwitter.data.auth.datasource

import com.connor.kwitter.domain.auth.model.RefreshRequest
import com.connor.kwitter.domain.auth.model.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ErrorBody(
    val code: String = "",
    val message: String = ""
)

sealed interface RefreshResult {
    data class Success(val tokenResponse: TokenResponse) : RefreshResult
    data object StaleToken : RefreshResult
    data object InvalidSession : RefreshResult
    data object TransientError : RefreshResult
}

class TokenRefresher(
    private val tokenDataSource: TokenDataSource,
    private val baseUrl: String
) {
    private val refreshClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private companion object {
        const val REFRESH_PATH = "/v1/auth/refresh"
        const val STALE_REFRESH_TOKEN = "STALE_REFRESH_TOKEN"
    }

    suspend fun refresh(): RefreshResult {
        val refreshToken = tokenDataSource.getRefreshToken()
            ?: return RefreshResult.InvalidSession

        val response = try {
            refreshClient.post(baseUrl.trimEnd('/') + REFRESH_PATH) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }
        } catch (_: Exception) {
            return RefreshResult.TransientError
        }

        return when {
            response.status.isSuccess() -> {
                val tokenResponse = response.body<TokenResponse>()
                tokenDataSource.updateTokens(
                    accessToken = tokenResponse.token,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn
                )
                RefreshResult.Success(tokenResponse)
            }

            response.status.value == 409 -> {
                val error = try { response.body<ErrorBody>() } catch (_: Exception) { null }
                if (error?.code == STALE_REFRESH_TOKEN) {
                    RefreshResult.StaleToken
                } else {
                    RefreshResult.TransientError
                }
            }

            response.status.value == 401 -> {
                tokenDataSource.clearTokens()
                RefreshResult.InvalidSession
            }

            else -> RefreshResult.TransientError
        }
    }
}
