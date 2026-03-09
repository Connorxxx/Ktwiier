package com.connor.kwitter.data.media.datasource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.catch
import com.connor.kwitter.domain.media.model.MediaError
import com.connor.kwitter.domain.media.model.MediaUploadResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class MediaRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val MEDIA_UPLOAD_PATH = "/v1/media/upload"
    }

    context(_: Raise<MediaError>)
    suspend fun uploadMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): MediaUploadResult = catch({
        val response: HttpResponse = httpClient.submitFormWithBinaryData(
            url = endpoint(MEDIA_UPLOAD_PATH),
            formData = formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }
        )
        handleResponse(response) { it.body() }
    }) {
        raise(MediaError.NetworkError("Media upload failed: ${it.message}"))
    }

    context(_: Raise<MediaError>)
    private suspend fun <T> handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                MediaError.Unauthorized("Authentication required")
            )
            response.status.value == 404 -> raise(
                MediaError.NotFound("Resource not found")
            )
            response.status.value in 400..499 -> raise(
                MediaError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                MediaError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                MediaError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}
