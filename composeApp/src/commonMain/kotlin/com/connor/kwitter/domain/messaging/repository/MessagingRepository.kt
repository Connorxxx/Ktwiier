package com.connor.kwitter.domain.messaging.repository

import arrow.core.Either
import com.connor.kwitter.domain.messaging.model.ConversationList
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageList
import com.connor.kwitter.domain.messaging.model.MessagesReadEvent
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.model.NewMessageEvent
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    suspend fun getConversations(limit: Int, offset: Int): Either<MessagingError, ConversationList>
    suspend fun getMessages(conversationId: String, limit: Int, offset: Int): Either<MessagingError, MessageList>
    suspend fun sendMessage(recipientId: String, content: String, imageUrl: String? = null): Either<MessagingError, Message>
    suspend fun markAsRead(conversationId: String): Either<MessagingError, Unit>
    val newMessageEvents: Flow<NewMessageEvent>
    val messagesReadEvents: Flow<MessagesReadEvent>
}
