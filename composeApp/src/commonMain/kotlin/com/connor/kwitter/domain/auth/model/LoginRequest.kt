package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

/**
 * 登录接口请求
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)
