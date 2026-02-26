package com.connor.kwitter.data.notification

import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NotificationService(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val WS_PATH = "/v1/notifications/ws"
        val BACKOFF_DELAYS = longArrayOf(1000, 2000, 4000, 8000, 16000)
        const val PING_INTERVAL_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var connectionJob: Job? = null
    private var currentSession: WebSocketSessionHolder? = null

    fun connect(scope: CoroutineScope) {
        disconnect()
        connectionJob = scope.launch {
            var retryCount = 0
            while (isActive) {
                _connectionState.value = ConnectionState.Connecting
                try {
                    val wsUrl = baseUrl
                        .replace("http://", "ws://")
                        .replace("https://", "wss://")
                        .trimEnd('/') + WS_PATH

                    httpClient.webSocket(wsUrl) {
                        _connectionState.value = ConnectionState.Connected
                        retryCount = 0
                        currentSession = WebSocketSessionHolder(this)

                        val pingJob = launch {
                            while (isActive) {
                                delay(PING_INTERVAL_MS)
                                try {
                                    outgoing.send(Frame.Text("""{"type":"ping"}"""))
                                } catch (_: Exception) {
                                    break
                                }
                            }
                        }

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    handleMessage(frame.readText())
                                }
                            }
                        } finally {
                            pingJob.cancel()
                            currentSession = null
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection failed or closed unexpectedly
                }

                _connectionState.value = ConnectionState.Disconnected
                currentSession = null

                if (!isActive) break
                val delayMs = BACKOFF_DELAYS[retryCount.coerceAtMost(BACKOFF_DELAYS.lastIndex)]
                delay(delayMs)
                if (retryCount < Int.MAX_VALUE) {
                    retryCount++
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        currentSession = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun sendSubscribePost(postId: Long) {
        currentSession?.send("""{"type":"subscribe_post","postId":$postId}""")
    }

    suspend fun sendUnsubscribePost(postId: Long) {
        currentSession?.send("""{"type":"unsubscribe_post","postId":$postId}""")
    }

    private suspend fun handleMessage(text: String) {
        // Backwards compatibility: raw "auth_revoked" text
        if (text == "auth_revoked") {
            _authEvents.emit(AuthEvent.ForceLogout("Session revoked by server"))
            return
        }

        val jsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return
        }

        val type = jsonObject["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "new_post" -> parseNewPost(jsonObject)?.let { _notificationEvents.emit(it) }
            "post_liked" -> parsePostLiked(jsonObject)?.let { _notificationEvents.emit(it) }
            "new_message" -> parseNewMessage(jsonObject)?.let { _notificationEvents.emit(it) }
            "messages_read" -> parseMessagesRead(jsonObject)?.let { _notificationEvents.emit(it) }
            "message_recalled" -> parseMessageRecalled(jsonObject)?.let { _notificationEvents.emit(it) }
            "typing_indicator" -> parseTypingIndicator(jsonObject)?.let { _notificationEvents.emit(it) }
            "presence_snapshot" -> parsePresenceSnapshot(jsonObject)?.let { _notificationEvents.emit(it) }
            "user_presence_changed" -> parseUserPresenceChanged(jsonObject)?.let { _notificationEvents.emit(it) }
            "error" -> { /* Log or ignore server errors for now */ }
            "connected", "subscribed", "unsubscribed", "pong" -> { /* Acknowledged, no action */ }
        }
    }

    private fun parseNewPost(jsonObject: JsonObject): NotificationEvent.NewPostCreated? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.NewPostCreated.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePostLiked(jsonObject: JsonObject): NotificationEvent.PostLiked? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.PostLiked.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNewMessage(jsonObject: JsonObject): NotificationEvent.NewMessage? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.NewMessage.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMessagesRead(jsonObject: JsonObject): NotificationEvent.MessagesRead? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.MessagesRead.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMessageRecalled(jsonObject: JsonObject): NotificationEvent.MessageRecalled? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.MessageRecalled.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTypingIndicator(jsonObject: JsonObject): NotificationEvent.TypingIndicator? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.TypingIndicator.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUserPresenceChanged(jsonObject: JsonObject): NotificationEvent.UserPresenceChanged? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.UserPresenceChanged.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePresenceSnapshot(jsonObject: JsonObject): NotificationEvent.PresenceSnapshot? {
        val data = jsonObject["data"] ?: return null
        return try {
            json.decodeFromJsonElement(NotificationEvent.PresenceSnapshot.serializer(), data)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendTyping(conversationId: Long) {
        currentSession?.send("""{"type":"typing","conversationId":$conversationId}""")
    }

    suspend fun sendStopTyping(conversationId: Long) {
        currentSession?.send("""{"type":"stop_typing","conversationId":$conversationId}""")
    }
}

private class WebSocketSessionHolder(
    private val session: io.ktor.websocket.WebSocketSession
) {
    suspend fun send(text: String) {
        try {
            session.outgoing.send(Frame.Text(text))
        } catch (_: Exception) {
            // Session may be closed
        }
    }
}


