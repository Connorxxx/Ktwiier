package com.connor.kwitter.data.notification

import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NotificationService(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val SSE_PATH = "/v1/notifications/stream"
        val BACKOFF_DELAYS = longArrayOf(1000, 2000, 4000, 8000, 16000)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var connectionJob: Job? = null

    fun connect(scope: CoroutineScope) {
        disconnect()
        connectionJob = scope.launch {
            var retryCount = 0
            while (isActive) {
                _connectionState.value = ConnectionState.Connecting
                try {
                    httpClient.sse(
                        urlString = "$baseUrl$SSE_PATH",
                        showCommentEvents = false
                    ) {
                        _connectionState.value = ConnectionState.Connected
                        retryCount = 0

                        incoming.collect { sseEvent ->
                            handleSseEvent(sseEvent.event, sseEvent.data)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection failed or closed unexpectedly
                }

                _connectionState.value = ConnectionState.Disconnected

                if (!isActive) break
                val delayMs = BACKOFF_DELAYS[retryCount.coerceAtMost(BACKOFF_DELAYS.lastIndex)]
                delay(delayMs)
                retryCount++
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun handleSseEvent(eventType: String?, data: String?) {
        when (eventType) {
            "new_post" -> parseData<NotificationEvent.NewPostCreated>(data)?.let { _notificationEvents.emit(it) }
            "post_liked" -> parseData<NotificationEvent.PostLiked>(data)?.let { _notificationEvents.emit(it) }
            "post_unliked" -> parseData<NotificationEvent.PostUnliked>(data)?.let { _notificationEvents.emit(it) }
            "new_message" -> parseData<NotificationEvent.NewMessage>(data)?.let { _notificationEvents.emit(it) }
            "messages_read" -> parseData<NotificationEvent.MessagesRead>(data)?.let { _notificationEvents.emit(it) }
            "message_recalled" -> parseData<NotificationEvent.MessageRecalled>(data)?.let { _notificationEvents.emit(it) }
            "typing_indicator" -> parseData<NotificationEvent.TypingIndicator>(data)?.let { _notificationEvents.emit(it) }
            "presence_snapshot" -> parseData<NotificationEvent.PresenceSnapshot>(data)?.let { _notificationEvents.emit(it) }
            "user_presence_changed" -> parseData<NotificationEvent.UserPresenceChanged>(data)?.let { _notificationEvents.emit(it) }
            "auth_revoked" -> _authEvents.emit(AuthEvent.ForceLogout(data ?: "Session revoked by server"))
            "connected", "subscribed", "unsubscribed" -> { /* ack */ }
        }
    }

    private inline fun <reified T> parseData(data: String?): T? {
        if (data == null) return null
        return try {
            json.decodeFromString<T>(data)
        } catch (_: Exception) {
            null
        }
    }

    // Commands via REST

    suspend fun subscribeToPost(postId: Long) {
        try {
            httpClient.post("$baseUrl/v1/notifications/posts/$postId/subscribe")
        } catch (_: Exception) {
            // Best-effort
        }
    }

    suspend fun unsubscribeFromPost(postId: Long) {
        try {
            httpClient.delete("$baseUrl/v1/notifications/posts/$postId/subscribe")
        } catch (_: Exception) {
            // Best-effort
        }
    }

    suspend fun sendTyping(conversationId: Long, isTyping: Boolean) {
        try {
            httpClient.put("$baseUrl/v1/messaging/conversations/$conversationId/typing") {
                contentType(ContentType.Application.Json)
                setBody(TypingRequest(isTyping))
            }
        } catch (_: Exception) {
            // Best-effort
        }
    }
}

@Serializable
private data class TypingRequest(val isTyping: Boolean)
