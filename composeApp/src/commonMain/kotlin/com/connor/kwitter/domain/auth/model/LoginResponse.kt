package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

/**
 * 登录接口响应
 */
@Serializable
data class LoginResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val token: String
)
