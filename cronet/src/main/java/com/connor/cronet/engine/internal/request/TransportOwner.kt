package com.connor.cronet.engine.internal.request

import kotlinx.coroutines.Job

internal sealed interface TransportOwner {
    data class RequestCall(val job: Job) : TransportOwner
    data object StreamSession : TransportOwner
}
