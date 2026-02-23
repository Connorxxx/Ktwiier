package com.connor.kwitter.core.util

import com.connor.kwitter.core.di.BASE_URL

fun resolveBackendUrl(url: String): String {
    val trimmedUrl = url.trim()
    if (trimmedUrl.startsWith("http://", ignoreCase = true) ||
        trimmedUrl.startsWith("https://", ignoreCase = true)
    ) {
        return trimmedUrl
    }

    val baseUrl = BASE_URL.trimEnd('/')
    return if (trimmedUrl.startsWith("/")) {
        "$baseUrl$trimmedUrl"
    } else {
        "$baseUrl/$trimmedUrl"
    }
}
