package com.connor.kwitter.data.messaging.datasource

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.ConversationList
import com.connor.kwitter.domain.messaging.model.ConversationUser
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageList
import com.connor.kwitter.domain.messaging.model.MessagingError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

class MessagingRemoteDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private companion object {
        const val CONVERSATIONS_PATH = "/v1/conversations"
        const val MESSAGES_PATH = "/v1/messages"
    }

    suspend fun getConversations(
        limit: Int,
        offset: Int
    ): Either<MessagingError, ConversationList> = either {
        try {
            val response: HttpResponse = httpClient.get(endpoint(CONVERSATIONS_PATH)) {
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) {
                it.body<ConversationListResponseDto>().toDomain()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun getMessages(
        conversationId: String,
        limit: Int,
        offset: Int
    ): Either<MessagingError, MessageList> = either {
        try {
            val response: HttpResponse = httpClient.get(
                endpoint("$CONVERSATIONS_PATH/$conversationId/messages")
            ) {
                parameter("limit", limit)
                parameter("offset", offset)
            }
            handleResponse(response) {
                it.body<MessageListResponseDto>().toDomain()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun sendMessage(
        recipientId: String,
        content: String,
        imageUrl: String?,
        replyToMessageId: String? = null
    ): Either<MessagingError, Message> = either {
        try {
            val response: HttpResponse = httpClient.post(
                endpoint("$CONVERSATIONS_PATH/messages")
            ) {
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequestDto(recipientId, content, imageUrl, replyToMessageId))
            }
            handleResponse(response) {
                it.body<MessageDto>().toDomain()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun deleteMessage(
        messageId: String
    ): Either<MessagingError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.delete(
                endpoint("$MESSAGES_PATH/$messageId")
            )
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun recallMessage(
        messageId: String
    ): Either<MessagingError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.put(
                endpoint("$MESSAGES_PATH/$messageId/recall")
            )
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    suspend fun markAsRead(
        conversationId: String
    ): Either<MessagingError, Unit> = either {
        try {
            val response: HttpResponse = httpClient.put(
                endpoint("$CONVERSATIONS_PATH/$conversationId/read")
            )
            handleResponse(response) { }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            raise(MessagingError.NetworkError("Network request failed: ${e.message}"))
        }
    }

    private suspend fun <T> arrow.core.raise.Raise<MessagingError>.handleResponse(
        response: HttpResponse,
        onSuccess: suspend (HttpResponse) -> T
    ): T {
        return when {
            response.status.isSuccess() -> onSuccess(response)
            response.status.value == 401 -> raise(
                MessagingError.Unauthorized("Authentication required")
            )
            response.status.value == 404 -> raise(
                MessagingError.NotFound("Not found")
            )
            response.status.value in 400..499 -> raise(
                MessagingError.ClientError(
                    code = response.status.value,
                    message = "Request failed: ${response.status.description}"
                )
            )
            response.status.value in 500..599 -> raise(
                MessagingError.ServerError(
                    code = response.status.value,
                    message = "Server error: ${response.status.description}"
                )
            )
            else -> raise(
                MessagingError.Unknown("Unexpected status: ${response.status.value}")
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path
}

@Serializable
private data class ConversationUserDto(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String? = null
)

@Serializable
private data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val imageUrl: String? = null,
    val readAt: Long? = null,
    val createdAt: Long,
    val replyToMessageId: String? = null,
    val deletedAt: Long? = null,
    val recalledAt: Long? = null
)

@Serializable
private data class ConversationDto(
    val id: String,
    val otherUser: ConversationUserDto,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int,
    val createdAt: Long
)

@Serializable
private data class ConversationListResponseDto(
    val conversations: List<ConversationDto>,
    val hasMore: Boolean = false
)

@Serializable
private data class MessageListResponseDto(
    val messages: List<MessageDto>,
    val hasMore: Boolean = false
)

@Serializable
private data class SendMessageRequestDto(
    val recipientId: String,
    val content: String,
    val imageUrl: String? = null,
    val replyToMessageId: String? = null
)

private fun ConversationUserDto.toDomain(): ConversationUser = ConversationUser(
    id = id,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl
)

private fun MessageDto.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    imageUrl = imageUrl,
    readAt = readAt,
    createdAt = createdAt,
    replyToMessageId = replyToMessageId,
    deletedAt = deletedAt,
    recalledAt = recalledAt
)

private fun ConversationDto.toDomain(): Conversation = Conversation(
    id = id,
    otherUser = otherUser.toDomain(),
    lastMessage = lastMessage?.toDomain(),
    unreadCount = unreadCount,
    createdAt = createdAt
)

private fun ConversationListResponseDto.toDomain(): ConversationList = ConversationList(
    conversations = conversations.map { it.toDomain() },
    hasMore = hasMore
)

private fun MessageListResponseDto.toDomain(): MessageList = MessageList(
    messages = messages.map { it.toDomain() },
    hasMore = hasMore
)
