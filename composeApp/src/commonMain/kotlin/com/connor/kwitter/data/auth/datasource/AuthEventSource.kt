package com.connor.kwitter.data.auth.datasource

import com.connor.kwitter.domain.auth.model.AuthEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AuthEventSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val WS_PATH = "/v1/notifications/ws"
        const val MAX_RETRIES = 5
        val BACKOFF_DELAYS = longArrayOf(1000, 2000, 4000, 8000, 16000)
    }

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var connectionJob: Job? = null

    fun connect(scope: CoroutineScope) {
        disconnect()
        connectionJob = scope.launch {
            var retryCount = 0
            while (retryCount <= MAX_RETRIES) {
                try {
                    val wsUrl = baseUrl
                        .replace("http://", "ws://")
                        .replace("https://", "wss://")
                        .trimEnd('/') + WS_PATH

                    httpClient.webSocket(wsUrl) {
                        retryCount = 0
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                if (text == "auth_revoked") {
                                    _events.emit(AuthEvent.ForceLogout("Session revoked by server"))
                                    return@webSocket
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection failed or closed unexpectedly
                }

                if (retryCount >= MAX_RETRIES) break
                val delayMs = BACKOFF_DELAYS[retryCount.coerceAtMost(BACKOFF_DELAYS.lastIndex)]
                delay(delayMs)
                retryCount++
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }
}
