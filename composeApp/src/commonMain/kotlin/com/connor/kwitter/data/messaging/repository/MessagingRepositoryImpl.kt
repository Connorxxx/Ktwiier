package com.connor.kwitter.data.messaging.repository

import arrow.core.Either
import com.connor.kwitter.data.messaging.datasource.MessagingRemoteDataSource
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.messaging.model.ConversationList
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageList
import com.connor.kwitter.domain.messaging.model.MessagesReadEvent
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.model.NewMessageEvent
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import com.connor.kwitter.domain.notification.model.NotificationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

class MessagingRepositoryImpl(
    private val remoteDataSource: MessagingRemoteDataSource,
    private val notificationService: NotificationService
) : MessagingRepository {

    override suspend fun getConversations(
        limit: Int,
        offset: Int
    ): Either<MessagingError, ConversationList> =
        remoteDataSource.getConversations(limit, offset)

    override suspend fun getMessages(
        conversationId: String,
        limit: Int,
        offset: Int
    ): Either<MessagingError, MessageList> =
        remoteDataSource.getMessages(conversationId, limit, offset)

    override suspend fun sendMessage(
        recipientId: String,
        content: String,
        imageUrl: String?
    ): Either<MessagingError, Message> =
        remoteDataSource.sendMessage(recipientId, content, imageUrl)

    override suspend fun markAsRead(
        conversationId: String
    ): Either<MessagingError, Unit> =
        remoteDataSource.markAsRead(conversationId)

    override val newMessageEvents: Flow<NewMessageEvent> =
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.NewMessage>()
            .map { event ->
                NewMessageEvent(
                    messageId = event.messageId,
                    conversationId = event.conversationId,
                    senderDisplayName = event.senderDisplayName,
                    senderUsername = event.senderUsername,
                    contentPreview = event.contentPreview,
                    timestamp = event.timestamp
                )
            }

    override val messagesReadEvents: Flow<MessagesReadEvent> =
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.MessagesRead>()
            .map { event ->
                MessagesReadEvent(
                    conversationId = event.conversationId,
                    readByUserId = event.readByUserId,
                    timestamp = event.timestamp
                )
            }
}
