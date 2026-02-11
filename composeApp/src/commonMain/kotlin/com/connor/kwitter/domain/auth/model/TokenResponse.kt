package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val token: String,
    val refreshToken: String,
    val expiresIn: Long
)
