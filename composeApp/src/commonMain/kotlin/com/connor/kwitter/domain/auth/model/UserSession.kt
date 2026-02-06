package com.connor.kwitter.domain.auth.model

sealed interface UserSession {
    data class Authenticated(val token: AuthToken) : UserSession
    data object Unauthenticated : UserSession
}
