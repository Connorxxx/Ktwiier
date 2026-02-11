package com.connor.kwitter.domain.auth.model

sealed interface AuthEvent {
    data class ForceLogout(val message: String) : AuthEvent
}
