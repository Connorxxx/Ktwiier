package com.connor.kwitter.domain.messaging.model

data class MessageSearchItem(
    val message: Message,
    val highlightedContent: String
)
