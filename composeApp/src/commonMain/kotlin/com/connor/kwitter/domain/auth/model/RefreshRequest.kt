package com.connor.kwitter.domain.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(val refreshToken: String)
