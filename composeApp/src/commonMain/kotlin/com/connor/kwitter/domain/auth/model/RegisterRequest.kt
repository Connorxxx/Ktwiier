package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 用户注册请求
 */
@Serializable
data class RegisterRequest(
    val email: String,
    @SerialName("displayName")
    val name: String,
    val password: String
)
