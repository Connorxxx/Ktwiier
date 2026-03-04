package com.connor.cronet.engine.internal.request.mapping

import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.InternalAPI

internal sealed interface CronetRequestBody {
    data object NoContent : CronetRequestBody

    data class ByteArrayBody(
        val bytes: ByteArray,
    ) : CronetRequestBody

    data class ReadChannelBody(
        val content: OutgoingContent.ReadChannelContent,
    ) : CronetRequestBody

    data class WriteChannelBody(
        val content: OutgoingContent.WriteChannelContent,
    ) : CronetRequestBody
}

@OptIn(InternalAPI::class)
internal fun HttpRequestData.classifyRequestBody(): CronetRequestBody {
    return when (val unwrapped = body.unwrapContent()) {
        is OutgoingContent.NoContent -> CronetRequestBody.NoContent
        is OutgoingContent.ByteArrayContent -> CronetRequestBody.ByteArrayBody(bytes = unwrapped.bytes())
        is OutgoingContent.ReadChannelContent -> CronetRequestBody.ReadChannelBody(content = unwrapped)
        is OutgoingContent.WriteChannelContent -> CronetRequestBody.WriteChannelBody(content = unwrapped)
        is OutgoingContent.ContentWrapper -> error("unreachable: wrappers must be unwrapped before classification")
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedOutgoingContentException(unwrapped)
    }
}

private tailrec fun OutgoingContent.unwrapContent(): OutgoingContent {
    return when (this) {
        is OutgoingContent.ContentWrapper -> delegate().unwrapContent()
        else -> this
    }
}
