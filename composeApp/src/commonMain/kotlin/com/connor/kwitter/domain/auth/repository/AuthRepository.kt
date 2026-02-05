package com.connor.kwitter.domain.auth.repository

import arrow.core.Either
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import kotlinx.coroutines.flow.Flow

/**
 * 认证仓储接口
 * 定义认证相关的业务操作，使用 Arrow Either 进行错误处理
 */
interface AuthRepository {
    /**
     * 用户注册
     * @return Either.Left(AuthError) 注册失败
     * @return Either.Right(AuthToken) 注册成功，返回认证令牌
     */
    suspend fun register(
        email: String,
        name: String,
        password: String
    ): Either<AuthError, AuthToken>

    /**
     * 监听本地存储的令牌（响应式）
     * @return null 表示没有存储的令牌
     */
    val token: Flow<AuthToken?>

    /**
     * 保存令牌到本地
     */
    suspend fun saveToken(token: AuthToken): Either<AuthError, Unit>

    /**
     * 清除本地令牌
     */
    suspend fun clearToken(): Either<AuthError, Unit>
}
