package com.connor.kwitter.domain.auth.model

/**
 * 认证错误类型
 * 使用 sealed class 确保类型安全和穷尽性检查
 */
sealed class AuthError {
    /**
     * 网络错误（连接失败、超时等）
     */
    data class NetworkError(val message: String) : AuthError()

    /**
     * 服务器错误（5xx）
     */
    data class ServerError(val code: Int, val message: String) : AuthError()

    /**
     * 客户端错误（4xx）
     */
    data class ClientError(val code: Int, val message: String) : AuthError()

    /**
     * 无效凭证（邮箱格式错误、密码太短等）
     */
    data class InvalidCredentials(val message: String) : AuthError()

    /**
     * 存储错误（DataStore 读写失败）
     */
    data class StorageError(val message: String) : AuthError()

    /**
     * 未知错误
     */
    data class Unknown(val message: String) : AuthError()

    data class SessionRevoked(val message: String) : AuthError()
}
