package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

/**
 * 注册接口响应
 */
@Serializable
data class RegisterResponse(
    val token: String,
    val id: String? = null
)
