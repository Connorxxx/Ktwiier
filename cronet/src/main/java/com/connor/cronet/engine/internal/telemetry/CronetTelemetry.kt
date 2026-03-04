package com.connor.cronet.engine.internal.telemetry

internal interface CronetTelemetry {
    fun onProviderSelected(providerName: String, providerVersion: String)

    fun onEngineShutdownFailure(cause: Throwable)
}

internal data object NoopCronetTelemetry : CronetTelemetry {
    override fun onProviderSelected(providerName: String, providerVersion: String) = Unit

    override fun onEngineShutdownFailure(cause: Throwable) = Unit
}
