package com.connor.kwitter.data.auth.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.RegisterRequest
import com.connor.kwitter.domain.auth.model.RegisterResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * 认证远程数据源
 * 负责处理与认证相关的 HTTP 请求
 */
class AuthRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val REGISTER_PATH = "/v1/auth/register"
    }

    /**
     * 用户注册
     */
    suspend fun register(
        email: String,
        name: String,
        password: String
    ): Either<AuthError, RegisterResponse> = either {
        try {
            val request = RegisterRequest(
                email = email,
                name = name,
                password = password
            )

            val response: HttpResponse = httpClient.post(registerEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when {
                response.status.isSuccess() -> {
                    response.body<RegisterResponse>()
                }
                response.status.value in 400..499 -> {
                    raise(
                        AuthError.ClientError(
                            code = response.status.value,
                            message = "Registration failed: ${response.status.description}"
                        )
                    )
                }
                response.status.value in 500..599 -> {
                    raise(
                        AuthError.ServerError(
                            code = response.status.value,
                            message = "Server error: ${response.status.description}"
                        )
                    )
                }
                else -> {
                    raise(
                        AuthError.Unknown(
                            message = "Unexpected status: ${response.status.value}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(
                AuthError.NetworkError(
                    message = "Network request failed: ${e.message}"
                )
            )
        }
    }

    private val registerEndpoint: String
        get() = baseUrl.trimEnd('/') + REGISTER_PATH
}
