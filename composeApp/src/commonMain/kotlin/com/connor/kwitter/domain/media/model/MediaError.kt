package com.connor.kwitter.domain.media.model

sealed class MediaError {
    data class NetworkError(val message: String) : MediaError()
    data class ServerError(val code: Int, val message: String) : MediaError()
    data class ClientError(val code: Int, val message: String) : MediaError()
    data class Unauthorized(val message: String) : MediaError()
    data class NotFound(val message: String) : MediaError()
    data class Unknown(val message: String) : MediaError()
}
