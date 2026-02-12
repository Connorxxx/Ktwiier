package com.connor.kwitter.domain.messaging.model

sealed class MessagingError {
    data class NetworkError(val message: String) : MessagingError()
    data class ServerError(val code: Int, val message: String) : MessagingError()
    data class ClientError(val code: Int, val message: String) : MessagingError()
    data class Unauthorized(val message: String) : MessagingError()
    data class NotFound(val message: String) : MessagingError()
    data class Unknown(val message: String) : MessagingError()
}
