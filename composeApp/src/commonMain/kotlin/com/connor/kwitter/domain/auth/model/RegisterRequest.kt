package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

/**
 * 用户注册请求
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String
)
