package com.connor.kwitter.domain.user.model

sealed class UserError {
    data class NetworkError(val message: String) : UserError()
    data class ServerError(val code: Int, val message: String) : UserError()
    data class ClientError(val code: Int, val message: String) : UserError()
    data class Unauthorized(val message: String) : UserError()
    data class NotFound(val message: String) : UserError()
    data class Unknown(val message: String) : UserError()
}
