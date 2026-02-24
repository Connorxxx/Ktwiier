package com.connor.kwitter.data.messaging.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import arrow.core.Either
import kotlin.time.Clock
import com.connor.kwitter.data.messaging.datasource.ConversationRemoteMediator
import com.connor.kwitter.data.messaging.datasource.MessageRemoteMediator
import com.connor.kwitter.data.messaging.datasource.MessagingRemoteDataSource
import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.MessageDao
import com.connor.kwitter.data.messaging.local.toDomain
import com.connor.kwitter.data.messaging.local.toEntity
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import com.connor.kwitter.domain.notification.model.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MessagingRepositoryImpl(
    private val remoteDataSource: MessagingRemoteDataSource,
    private val notificationService: NotificationService,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val repositoryScope: CoroutineScope
) : MessagingRepository {

    private val conversationsRefreshTrigger = MutableStateFlow(0L)
    private val _typingState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _onlineStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    init {
        repositoryScope.launch { observeNewMessages() }
        repositoryScope.launch { observeMessagesRead() }
        repositoryScope.launch { observeMessageRecalled() }
        repositoryScope.launch { observeTypingIndicators() }
        repositoryScope.launch { observePresence() }
    }

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    override val conversationsPaging: Flow<PagingData<Conversation>> =
        conversationsRefreshTrigger.flatMapLatest {
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                remoteMediator = ConversationRemoteMediator(
                    remoteDataSource = remoteDataSource,
                    conversationDao = conversationDao
                ),
                pagingSourceFactory = { conversationDao.getPagingSource() }
            ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
        }

    @OptIn(ExperimentalPagingApi::class)
    override fun messagesPaging(conversationId: String): Flow<PagingData<Message>> =
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            remoteMediator = MessageRemoteMediator(
                conversationId = conversationId,
                remoteDataSource = remoteDataSource,
                messageDao = messageDao
            ),
            pagingSourceFactory = { messageDao.getPagingSource(conversationId) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override suspend fun sendMessage(
        recipientId: String,
        content: String,
        imageUrl: String?,
        replyToMessageId: String?
    ): Either<MessagingError, Message> =
        remoteDataSource.sendMessage(recipientId, content, imageUrl, replyToMessageId).onRight { message ->
            // Insert sent message into Room at the newest position
            val minIndex = messageDao.getMinOrderIndex(message.conversationId) ?: 0
            messageDao.insert(message.toEntity(orderIndex = minIndex - 1))
            // Refresh conversations to reflect new message
            conversationsRefreshTrigger.update { it + 1 }
        }

    override suspend fun deleteMessage(
        messageId: String
    ): Either<MessagingError, Unit> =
        remoteDataSource.deleteMessage(messageId).onRight {
            val now = Clock.System.now().toEpochMilliseconds()
            messageDao.markMessageAsDeleted(messageId, now)
            conversationDao.updateLastMessageDeleted(messageId, now)
        }

    override suspend fun recallMessage(
        messageId: String
    ): Either<MessagingError, Unit> =
        remoteDataSource.recallMessage(messageId).onRight {
            val now = Clock.System.now().toEpochMilliseconds()
            messageDao.markMessageAsRecalled(messageId, now)
            conversationDao.updateLastMessageRecalled(messageId, now)
        }

    override fun typingIndicators(conversationId: String): Flow<Boolean> =
        _typingState.map { it[conversationId] ?: false }

    override fun onlineStatus(): Flow<Map<String, Boolean>> =
        _onlineStatus.asStateFlow()

    override fun sendTyping(conversationId: String) {
        repositoryScope.launch { notificationService.sendTyping(conversationId) }
    }

    override fun sendStopTyping(conversationId: String) {
        repositoryScope.launch { notificationService.sendStopTyping(conversationId) }
    }

    override suspend fun markAsRead(
        conversationId: String
    ): Either<MessagingError, Unit> =
        remoteDataSource.markAsRead(conversationId).onRight {
            conversationDao.updateUnreadCount(conversationId, 0)
        }

    private suspend fun observeNewMessages() {
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.NewMessage>()
            .collect { event ->
                // Insert new message into Room
                val newMessage = Message(
                    id = event.messageId,
                    conversationId = event.conversationId,
                    senderId = "",
                    content = event.contentPreview,
                    imageUrl = null,
                    readAt = null,
                    createdAt = event.timestamp
                )
                val minIndex = messageDao.getMinOrderIndex(event.conversationId) ?: 0
                messageDao.insert(newMessage.toEntity(orderIndex = minIndex - 1))

                // Update conversation in Room: bump to top + increment unread
                val existing = conversationDao.getById(event.conversationId)
                if (existing != null) {
                    val minConvIndex = conversationDao.getMinOrderIndex() ?: 0
                    conversationDao.insertOrReplace(
                        existing.copy(
                            lastMessageId = event.messageId,
                            lastMessageContent = event.contentPreview,
                            lastMessageSenderId = "",
                            lastMessageCreatedAt = event.timestamp,
                            lastMessageReadAt = null,
                            unreadCount = existing.unreadCount + 1,
                            orderIndex = minConvIndex - 1
                        )
                    )
                } else {
                    // New conversation not in cache — trigger refresh
                    conversationsRefreshTrigger.update { it + 1 }
                }
            }
    }

    private suspend fun observeMessagesRead() {
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.MessagesRead>()
            .collect { event ->
                // Update read receipts on sent messages in Room
                messageDao.markSentMessagesAsRead(
                    conversationId = event.conversationId,
                    senderId = event.readByUserId,
                    readAt = event.timestamp
                )
                conversationDao.updateUnreadCount(event.conversationId, 0)
            }
    }

    private suspend fun observeMessageRecalled() {
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.MessageRecalled>()
            .collect { event ->
                messageDao.markMessageAsRecalled(event.messageId, event.timestamp)
                conversationDao.updateLastMessageRecalled(event.messageId, event.timestamp)
            }
    }

    private suspend fun observeTypingIndicators() {
        notificationService.notificationEvents
            .filterIsInstance<NotificationEvent.TypingIndicator>()
            .collect { event ->
                _typingState.update { current ->
                    current + (event.conversationId to event.isTyping)
                }
            }
    }

    private suspend fun observePresence() {
        notificationService.notificationEvents
            .filter { event ->
                event is NotificationEvent.PresenceSnapshot ||
                    event is NotificationEvent.UserPresenceChanged
            }
            .collect { event ->
                when (event) {
                    is NotificationEvent.PresenceSnapshot -> {
                        // Presence v3 snapshot is a full baseline sync, not a delta.
                        _onlineStatus.update { current ->
                            current.toMutableMap().apply {
                                clear()
                                event.users.forEach { user ->
                                    this[user.userId] = user.isOnline
                                }
                            }
                        }
                    }

                    is NotificationEvent.UserPresenceChanged -> {
                        _onlineStatus.update { current ->
                            current + (event.userId to event.isOnline)
                        }
                    }

                    else -> Unit
                }
            }
    }
}
