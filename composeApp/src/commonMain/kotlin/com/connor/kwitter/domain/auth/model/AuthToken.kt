package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

/**
 * JWT 认证令牌
 */
@Serializable
data class AuthToken(
    val token: String,
    val userId: String? = null
)
