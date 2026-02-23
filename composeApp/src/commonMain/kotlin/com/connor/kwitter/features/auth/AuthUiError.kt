package com.connor.kwitter.features.auth

import com.connor.kwitter.domain.auth.model.AuthError

sealed interface AuthUiError {
    data class Network(val detail: String) : AuthUiError
    data class Server(val code: Int, val detail: String) : AuthUiError
    data class Client(val code: Int, val detail: String) : AuthUiError
    data class InvalidCredentials(val detail: String?) : AuthUiError
    data class Storage(val detail: String) : AuthUiError
    data class Unknown(val detail: String) : AuthUiError
    data class SessionRevoked(val detail: String) : AuthUiError
}

fun AuthError.toAuthUiError(includeInvalidDetail: Boolean): AuthUiError = when (this) {
    is AuthError.NetworkError -> AuthUiError.Network(message)
    is AuthError.ServerError -> AuthUiError.Server(code, message)
    is AuthError.ClientError -> AuthUiError.Client(code, message)
    is AuthError.InvalidCredentials -> {
        AuthUiError.InvalidCredentials(
            detail = if (includeInvalidDetail) message else null
        )
    }
    is AuthError.StorageError -> AuthUiError.Storage(message)
    is AuthError.Unknown -> AuthUiError.Unknown(message)
    is AuthError.SessionRevoked -> AuthUiError.SessionRevoked(message)
}
