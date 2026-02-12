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
    suspend fun sendMessage(recipientId: String, content: String, imageUrl: String? = null): Either<MessagingError, Message>
    suspend fun markAsRead(conversationId: String): Either<MessagingError, Unit>
}
