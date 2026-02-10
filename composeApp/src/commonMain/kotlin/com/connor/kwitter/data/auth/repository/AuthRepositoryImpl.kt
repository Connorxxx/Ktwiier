package com.connor.kwitter.data.auth.repository

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.model.UserSession
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * AuthRepository 实现
 * 协调远程数据源和本地数据源
 *
 * 职责：
 * 1. 监听本地token变化
 * 2. 自动验证token有效性
 * 3. token无效时自动清除（触发登出）
 */
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenDataSource: TokenDataSource
) : AuthRepository {

    // 使用独立的CoroutineScope，避免依赖外部scope
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 内部状态流，用于缓存验证后的session状态
    private val _sessionState = MutableStateFlow<UserSession>(UserSession.Unauthenticated)

    init {
        // 监听token变化，自动验证有效性
        repositoryScope.launch {
            tokenDataSource.token
                .distinctUntilChanged() // 只在token真正变化时触发
                .collect { token ->
                    if (token != null) {
                        // 有token时，验证其有效性
                        val isValid = validateToken(token).getOrElse { false }

                        if (isValid) {
                            // Token有效，设置为已认证状态
                            _sessionState.value = UserSession.Authenticated(token)
                        } else {
                            // Token无效，自动清除
                            tokenDataSource.clearToken()
                            _sessionState.value = UserSession.Unauthenticated
                        }
                    } else {
                        // 无token，设置为未认证状态
                        _sessionState.value = UserSession.Unauthenticated
                    }
                }
        }
    }

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

    override suspend fun login(
        email: String,
        password: String
    ): Either<AuthError, AuthToken> {
        return remoteDataSource.login(email, password)
            .map { response -> AuthToken(response.token) }
            .flatMap { token ->
                // 登录成功后自动保存 token
                tokenDataSource.saveToken(token).map { token }
            }
    }

    /**
     * 暴露session状态流
     * 这个Flow会自动验证token有效性并在无效时触发登出
     */
    override val session: Flow<UserSession> = _sessionState

    override suspend fun saveToken(token: AuthToken): Either<AuthError, Unit> {
        return tokenDataSource.saveToken(token)
    }

    override suspend fun clearToken(): Either<AuthError, Unit> {
        return tokenDataSource.clearToken()
    }

    override suspend fun validateToken(token: AuthToken): Either<AuthError, Boolean> {
        return remoteDataSource.validateToken(token.token)
    }
}
