package com.connor.cronet.engine.internal.request.mapping

import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import org.chromium.net.UrlResponseInfo

internal fun UrlResponseInfo.toKtorStatusCode(): HttpStatusCode {
    return HttpStatusCode(getHttpStatusCode(), getHttpStatusText())
}

internal fun UrlResponseInfo.toKtorHeaders(): Headers {
    val builder = HeadersBuilder()
    getAllHeadersAsList().forEach { (name, value) ->
        builder.append(name, value)
    }
    return builder.build()
}

internal fun UrlResponseInfo.toKtorProtocolVersion(): HttpProtocolVersion {
    val negotiated = getNegotiatedProtocol().trim().lowercase()
    if (negotiated.isEmpty()) return HttpProtocolVersion.HTTP_1_1

    return when {
        negotiated == "h2" || negotiated.startsWith("h2-") -> HttpProtocolVersion.HTTP_2_0
        negotiated == "h3" || negotiated.startsWith("h3-") -> HttpProtocolVersion.HTTP_3_0
        negotiated.startsWith("http/1.0") -> HttpProtocolVersion.HTTP_1_0
        negotiated.startsWith("http/1.1") -> HttpProtocolVersion.HTTP_1_1
        negotiated.startsWith("http/2") -> HttpProtocolVersion.HTTP_2_0
        negotiated.startsWith("http/3") -> HttpProtocolVersion.HTTP_3_0
        negotiated.startsWith("quic") -> HttpProtocolVersion.QUIC
        else -> HttpProtocolVersion.HTTP_1_1
    }
}
