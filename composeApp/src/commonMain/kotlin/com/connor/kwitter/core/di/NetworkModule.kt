package com.connor.kwitter.core.di

import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.domain.auth.model.RefreshRequest
import com.connor.kwitter.domain.auth.model.TokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

const val BASE_URL = "http://192.168.0.101:8080"
private const val REFRESH_PATH = "/v1/auth/refresh"

val networkModule = module {
    single {
        val tokenDataSource: TokenDataSource = get()

        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }

            install(WebSockets)

            install(Auth) {
                bearer {
                    loadTokens {
                        val accessToken = tokenDataSource.getAccessToken()
                        val refreshToken = tokenDataSource.getRefreshToken()
                        if (accessToken != null && refreshToken != null) {
                            BearerTokens(accessToken, refreshToken)
                        } else {
                            null
                        }
                    }

                    refreshTokens {
                        val refreshToken = tokenDataSource.getRefreshToken() ?: return@refreshTokens null

                        val response = client.post(BASE_URL.trimEnd('/') + REFRESH_PATH) {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refreshToken))
                        }

                        if (response.status.isSuccess()) {
                            val tokenResponse = response.body<TokenResponse>()
                            tokenDataSource.updateTokens(
                                accessToken = tokenResponse.token,
                                refreshToken = tokenResponse.refreshToken
                            )
                            BearerTokens(tokenResponse.token, tokenResponse.refreshToken)
                        } else {
                            tokenDataSource.clearTokens()
                            null
                        }
                    }

                    sendWithoutRequest { request ->
                        request.url.buildString().contains("/v1/auth/").not()
                    }
                }
            }
        }
    }
}
