package com.connor.cronet.engine

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

/**
 * Attribute key used to pass request-level Cronet annotations through Ktor request attributes.
 */
val CronetRequestAnnotationsAttributeKey: AttributeKey<MutableList<Any>> =
    AttributeKey("CronetRequestAnnotations")

/**
 * Adds a Cronet request annotation that will be forwarded to `UrlRequest.Builder.addRequestAnnotation`.
 */
fun HttpRequestBuilder.cronetRequestAnnotation(annotation: Any) {
    val annotations = attributes.getOrNull(CronetRequestAnnotationsAttributeKey)
        ?: mutableListOf<Any>().also { attributes.put(CronetRequestAnnotationsAttributeKey, it) }
    annotations += annotation
}
