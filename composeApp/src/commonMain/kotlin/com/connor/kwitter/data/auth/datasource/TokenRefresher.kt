package com.connor.kwitter.data.auth.datasource

import arrow.core.raise.fold
import com.connor.kwitter.domain.auth.model.RefreshRequest
import com.connor.kwitter.domain.auth.model.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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

private sealed interface RefreshAttempt {
    data class Response(val value: HttpResponse) : RefreshAttempt
    data class Failure(val result: RefreshResult) : RefreshAttempt
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

        val response = fold(
            block = {
                refreshClient.post(baseUrl.trimEnd('/') + REFRESH_PATH) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }
            },
            catch = { RefreshAttempt.Failure(RefreshResult.TransientError) },
            recover = { result: RefreshResult -> RefreshAttempt.Failure(result) },
            transform = { httpResponse -> RefreshAttempt.Response(httpResponse) }
        )

        val httpResponse = when (response) {
            is RefreshAttempt.Failure -> return response.result
            is RefreshAttempt.Response -> response.value
        }

        return when {
            httpResponse.status.isSuccess() -> {
                val tokenResponse = httpResponse.body<TokenResponse>()
                fold(
                    block = {
                        tokenDataSource.updateTokens(
                            accessToken = tokenResponse.token,
                            refreshToken = tokenResponse.refreshToken,
                            expiresIn = tokenResponse.expiresIn
                        )
                    },
                    catch = { RefreshResult.TransientError },
                    recover = { RefreshResult.TransientError },
                    transform = { RefreshResult.Success(tokenResponse) }
                )
            }

            httpResponse.status.value == 409 -> {
                val error = fold(
                    block = { httpResponse.body<ErrorBody>() },
                    catch = { null },
                    recover = { _: Unit -> null },
                    transform = { it }
                )
                if (error?.code == STALE_REFRESH_TOKEN) {
                    RefreshResult.StaleToken
                } else {
                    RefreshResult.TransientError
                }
            }

            httpResponse.status.value == 401 -> {
                fold(
                    block = { tokenDataSource.clearTokens() },
                    catch = {},
                    recover = {},
                    transform = {}
                )
                RefreshResult.InvalidSession
            }

            else -> RefreshResult.TransientError
        }
    }
}
