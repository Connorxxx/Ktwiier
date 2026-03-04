package com.connor.cronet.engine.internal.request.mapping

import com.connor.cronet.engine.CronetRequestAnnotationsAttributeKey
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forEachHeader
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.InternalAPI
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import org.chromium.net.CronetEngine
import org.chromium.net.UrlRequest

internal data class PreparedCronetRequest(
    val requestBuilder: UrlRequest.Builder,
    val requestBody: CronetRequestBody,
)

@OptIn(InternalAPI::class)
internal class CronetRequestBuilderMapper(
    private val cronetEngine: CronetEngine,
    private val callbackExecutor: Executor,
) {

    fun map(
        data: HttpRequestData,
        callContext: CoroutineContext,
        callback: UrlRequest.Callback,
    ): PreparedCronetRequest {
        val requestBody = data.classifyRequestBody()
        val requestBuilder = cronetEngine.newUrlRequestBuilder(
            data.url.toString(),
            callback,
            callbackExecutor,
        ).setHttpMethod(data.method.value)

        var hasContentType = false
        data.forEachHeader { key, value ->
            if (key.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                hasContentType = true
            }
            requestBuilder.addHeader(key, value)
        }

        data.attributes.getOrNull(CronetRequestAnnotationsAttributeKey)
            ?.forEach(requestBuilder::addRequestAnnotation)

        when (requestBody) {
            CronetRequestBody.NoContent -> Unit
            is CronetRequestBody.ByteArrayBody -> {
                if (requestBody.bytes.isEmpty()) {
                    return PreparedCronetRequest(
                        requestBuilder = requestBuilder,
                        requestBody = requestBody,
                    )
                }

                require(hasContentType) {
                    "Cronet upload requires Content-Type header when request body is present"
                }
                requestBuilder.setUploadDataProvider(
                    ByteArrayUploadDataProvider(payload = requestBody.bytes),
                    callbackExecutor,
                )
            }

            is CronetRequestBody.ReadChannelBody -> {
                if (requestBody.content.contentLength == 0L) {
                    return PreparedCronetRequest(
                        requestBuilder = requestBuilder,
                        requestBody = requestBody,
                    )
                }

                require(hasContentType) {
                    "Cronet upload requires Content-Type header when request body is present"
                }
                requestBuilder.setUploadDataProvider(
                    StreamingUploadDataProvider.fromReadChannelContent(requestBody.content),
                    callbackExecutor,
                )
            }

            is CronetRequestBody.WriteChannelBody -> {
                if (requestBody.content.contentLength == 0L) {
                    return PreparedCronetRequest(
                        requestBuilder = requestBuilder,
                        requestBody = requestBody,
                    )
                }

                require(hasContentType) {
                    "Cronet upload requires Content-Type header when request body is present"
                }
                requestBuilder.setUploadDataProvider(
                    StreamingUploadDataProvider.fromWriteChannelContent(
                        content = requestBody.content,
                        callContext = callContext,
                    ),
                    callbackExecutor,
                )
            }
        }

        return PreparedCronetRequest(
            requestBuilder = requestBuilder,
            requestBody = requestBody,
        )
    }
}
