package com.connor.kwitter.domain.post.model

sealed class PostError {
    data class NetworkError(val message: String) : PostError()
    data class ServerError(val code: Int, val message: String) : PostError()
    data class ClientError(val code: Int, val message: String) : PostError()
    data class Unauthorized(val message: String) : PostError()
    data class NotFound(val message: String) : PostError()
    data class Unknown(val message: String) : PostError()
}
