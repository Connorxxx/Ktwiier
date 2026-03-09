package com.connor.kwitter.domain.messaging.repository

import androidx.paging.PagingData
import arrow.core.raise.context.Raise
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.domain.messaging.model.MessagingError
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    val conversationsPaging: Flow<PagingData<Conversation>>
    fun messagesPaging(conversationId: Long): Flow<PagingData<Message>>
    suspend fun resolveConversationId(otherUserId: Long): Long?

    context(_: Raise<MessagingError>)
    suspend fun sendMessage(
        recipientId: Long,
        content: String,
        imageUrl: String? = null,
        replyToMessageId: Long? = null
    ): Message

    context(_: Raise<MessagingError>)
    suspend fun deleteMessage(messageId: Long)

    context(_: Raise<MessagingError>)
    suspend fun recallMessage(messageId: Long)

    context(_: Raise<MessagingError>)
    suspend fun markAsRead(conversationId: Long)

    fun setActiveConversation(conversationId: Long?)
    fun typingIndicators(conversationId: Long): Flow<Boolean>
    fun onlineStatus(): Flow<Map<Long, Boolean>>
    fun sendTyping(conversationId: Long)
    fun sendStopTyping(conversationId: Long)

    context(_: Raise<MessagingError>)
    suspend fun searchMessages(
        conversationId: Long,
        query: String
    ): List<MessageSearchItem>
}


