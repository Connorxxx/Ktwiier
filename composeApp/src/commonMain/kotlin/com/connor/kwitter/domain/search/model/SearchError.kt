package com.connor.kwitter.domain.search.model

sealed class SearchError {
    data class NetworkError(val message: String) : SearchError()
    data class ServerError(val code: Int, val message: String) : SearchError()
    data class ClientError(val code: Int, val message: String) : SearchError()
    data class Unauthorized(val message: String) : SearchError()
    data class NotFound(val message: String) : SearchError()
    data class Unknown(val message: String) : SearchError()
}
