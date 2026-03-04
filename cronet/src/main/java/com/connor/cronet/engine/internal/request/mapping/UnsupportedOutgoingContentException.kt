package com.connor.cronet.engine.internal.request.mapping

import io.ktor.http.content.OutgoingContent

internal class UnsupportedOutgoingContentException(
    content: OutgoingContent,
    detail: String? = null,
) : IllegalArgumentException(
    buildString {
        append("Unsupported OutgoingContent for Cronet engine: ")
        append(content::class.qualifiedName ?: content::class.simpleName ?: "unknown")
        if (detail != null) {
            append(". ")
            append(detail)
        }
    },
)
