package com.connor.kwitter.domain.messaging.repository

import androidx.paging.PagingData
import arrow.core.Either
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessagingError
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    val conversationsPaging: Flow<PagingData<Conversation>>
    fun messagesPaging(conversationId: String): Flow<PagingData<Message>>
    suspend fun sendMessage(
        recipientId: String,
        content: String,
        imageUrl: String? = null,
        replyToMessageId: String? = null
    ): Either<MessagingError, Message>
    suspend fun deleteMessage(messageId: String): Either<MessagingError, Unit>
    suspend fun recallMessage(messageId: String): Either<MessagingError, Unit>
    suspend fun markAsRead(conversationId: String): Either<MessagingError, Unit>
    fun setActiveConversation(conversationId: String?)
    fun typingIndicators(conversationId: String): Flow<Boolean>
    fun onlineStatus(): Flow<Map<String, Boolean>>
    fun sendTyping(conversationId: String)
    fun sendStopTyping(conversationId: String)
}
