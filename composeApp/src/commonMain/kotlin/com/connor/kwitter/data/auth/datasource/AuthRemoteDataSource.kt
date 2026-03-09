package com.connor.kwitter.data.auth.datasource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.catch
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthResponse
import com.connor.kwitter.domain.auth.model.LoginRequest
import com.connor.kwitter.domain.auth.model.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AuthRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val REGISTER_PATH = "/v1/auth/register"
        const val LOGIN_PATH = "/v1/auth/login"
    }

    context(_: Raise<AuthError>)
    suspend fun register(
        email: String,
        name: String,
        password: String
    ): AuthResponse = catch({
        val request = RegisterRequest(
            email = email,
            name = name,
            password = password
        )

        val response: HttpResponse = httpClient.post(endpoint(REGISTER_PATH)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        handleResponse(response) { it.body() }
    }) {
        raise(AuthError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<AuthError>)
    suspend fun login(
        email: String,
        password: String
    ): AuthResponse = catch({
        val request = LoginRequest(
            email = email,
            password = password
        )

        val response: HttpResponse = httpClient.post(endpoint(LOGIN_PATH)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        handleResponse(response) { it.body() }
    }) {
        raise(AuthError.NetworkError("Network request failed: ${it.message}"))
    }

    context(_: Raise<AuthError>)
    private suspend fun <T> handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                AuthError.InvalidCredentials("邮箱或密码错误")
            )
            response.status.value in 400..499 -> raise(
                AuthError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                AuthError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                AuthError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}
