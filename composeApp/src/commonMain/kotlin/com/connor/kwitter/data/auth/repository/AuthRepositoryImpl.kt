package com.connor.kwitter.data.auth.repository

import arrow.core.Either
import arrow.core.flatMap
import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.model.UserSession
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * AuthRepository 实现
 * 协调远程数据源和本地数据源
 */
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenDataSource: TokenDataSource
) : AuthRepository {

    override suspend fun register(
        email: String,
        name: String,
        password: String
    ): Either<AuthError, AuthToken> {
        // 调用远程接口注册
        return remoteDataSource.register(email, name, password)
            .map { response -> AuthToken(response.token) }
            .flatMap { token ->
                // 注册成功后自动保存 token
                tokenDataSource.saveToken(token).map { token }
            }
    }

    override val session: Flow<UserSession> = tokenDataSource.token.map { token ->
        if (token != null) {
            UserSession.Authenticated(token)
        } else {
            UserSession.Unauthenticated
        }
    }

    override suspend fun saveToken(token: AuthToken): Either<AuthError, Unit> {
        return tokenDataSource.saveToken(token)
    }

    override suspend fun clearToken(): Either<AuthError, Unit> {
        return tokenDataSource.clearToken()
    }
}
