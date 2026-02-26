package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val id: Long,
    val email: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val avatarUrl: String? = null,
    val createdAt: Long,
    val token: String,
    val refreshToken: String,
    val expiresIn: Long
)

