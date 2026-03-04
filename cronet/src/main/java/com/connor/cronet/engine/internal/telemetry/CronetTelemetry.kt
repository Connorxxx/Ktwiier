package com.connor.cronet.engine.internal.telemetry

internal interface CronetTelemetry {
    fun onProviderSelected(
        providerName: String,
        providerVersion: String,
        providerSource: CronetProviderSource,
    )

    fun onRequestFinished(event: CronetRequestTelemetryEvent)

    fun onEngineShutdownFailure(cause: Throwable)
}

internal enum class CronetProviderSource {
    AppPackaged,
    Fallback,
    Platform,
    Unknown,
}

internal enum class CronetRequestCompletionReason {
    Succeeded,
    Failed,
    Canceled,
}

internal data class CronetRequestTelemetryEvent(
    val method: String,
    val url: String,
    val durationMillis: Long,
    val statusCode: Int?,
    val negotiatedProtocol: String?,
    val completionReason: CronetRequestCompletionReason,
    val failure: CronetRequestFailure?,
)

internal sealed interface CronetRequestFailure {
    data class Cronet(val exception: CronetExceptionClassification) : CronetRequestFailure

    data class Timeout(val kind: TimeoutKind) : CronetRequestFailure

    data class Cancellation(
        val message: String?,
    ) : CronetRequestFailure

    data class Other(
        val throwableClass: String,
        val message: String?,
    ) : CronetRequestFailure
}

internal enum class TimeoutKind {
    Request,
    Connect,
    Socket,
}

internal sealed interface CronetExceptionClassification {
    data class Network(
        val errorCode: Int,
        val internalErrorCode: Int,
        val immediatelyRetryable: Boolean,
    ) : CronetExceptionClassification

    data class Quic(
        val errorCode: Int,
        val internalErrorCode: Int,
        val immediatelyRetryable: Boolean,
        val quicDetailedErrorCode: Int,
        val connectionCloseSource: Int,
    ) : CronetExceptionClassification

    data class Callback(
        val callbackCauseClass: String?,
        val callbackCauseMessage: String?,
    ) : CronetExceptionClassification

    data class Other(
        val throwableClass: String,
        val message: String?,
    ) : CronetExceptionClassification
}

internal data object NoopCronetTelemetry : CronetTelemetry {
    override fun onProviderSelected(
        providerName: String,
        providerVersion: String,
        providerSource: CronetProviderSource,
    ) = Unit

    override fun onRequestFinished(event: CronetRequestTelemetryEvent) = Unit

    override fun onEngineShutdownFailure(cause: Throwable) = Unit
}
