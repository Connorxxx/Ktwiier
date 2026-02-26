package com.connor.kwitter.domain.messaging.repository

import androidx.paging.PagingData
import arrow.core.Either
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.domain.messaging.model.MessagingError
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    val conversationsPaging: Flow<PagingData<Conversation>>
    fun messagesPaging(conversationId: Long): Flow<PagingData<Message>>
    suspend fun resolveConversationId(otherUserId: Long): Long?
    suspend fun sendMessage(
        recipientId: Long,
        content: String,
        imageUrl: String? = null,
        replyToMessageId: Long? = null
    ): Either<MessagingError, Message>
    suspend fun deleteMessage(messageId: Long): Either<MessagingError, Unit>
    suspend fun recallMessage(messageId: Long): Either<MessagingError, Unit>
    suspend fun markAsRead(conversationId: Long): Either<MessagingError, Unit>
    fun setActiveConversation(conversationId: Long?)
    fun typingIndicators(conversationId: Long): Flow<Boolean>
    fun onlineStatus(): Flow<Map<Long, Boolean>>
    fun sendTyping(conversationId: Long)
    fun sendStopTyping(conversationId: Long)
    suspend fun searchMessages(
        conversationId: Long,
        query: String
    ): Either<MessagingError, List<MessageSearchItem>>
}


