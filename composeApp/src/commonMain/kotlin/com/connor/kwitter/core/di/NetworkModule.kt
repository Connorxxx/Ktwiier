package com.connor.kwitter.core.di

import com.connor.kwitter.data.auth.datasource.RefreshResult
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.auth.datasource.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

const val BASE_URL = "http://192.168.0.101:8080" //"https://k.boxtor.uk"

val networkModule = module {
    single {
        TokenRefresher(
            tokenDataSource = get(),
            baseUrl = BASE_URL
        )
    }

    single {
        val tokenDataSource: TokenDataSource = get()
        val tokenRefresher: TokenRefresher = get()

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

            install(SSE)

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
                        when (val result = tokenRefresher.refresh()) {
                            is RefreshResult.Success -> BearerTokens(
                                result.tokenResponse.token,
                                result.tokenResponse.refreshToken
                            )

                            is RefreshResult.StaleToken -> {
                                val latestAccess = tokenDataSource.getAccessToken()
                                val latestRefresh = tokenDataSource.getRefreshToken()
                                if (latestAccess != null && latestRefresh != null) {
                                    BearerTokens(latestAccess, latestRefresh)
                                } else {
                                    null
                                }
                            }

                            is RefreshResult.InvalidSession -> null
                            is RefreshResult.TransientError -> null
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
