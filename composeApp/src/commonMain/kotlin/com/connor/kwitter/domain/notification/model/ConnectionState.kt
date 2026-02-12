package com.connor.kwitter.domain.notification.model

sealed interface ConnectionState {
    data object Connected : ConnectionState
    data object Connecting : ConnectionState
    data object Disconnected : ConnectionState
}
