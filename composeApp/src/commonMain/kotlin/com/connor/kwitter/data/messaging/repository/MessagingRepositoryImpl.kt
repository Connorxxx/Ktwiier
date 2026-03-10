package com.connor.kwitter.data.messaging.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.catch
import arrow.core.raise.fold
import com.connor.kwitter.data.messaging.datasource.ConversationRemoteMediator
import com.connor.kwitter.data.messaging.datasource.MessageRemoteMediator
import com.connor.kwitter.data.messaging.datasource.MessagingRemoteDataSource
import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.MessageDao
import com.connor.kwitter.data.messaging.local.toDomain
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.ConversationList
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import com.connor.kwitter.domain.notification.model.NotificationEvent
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private companion object {
        const val CONVERSATION_RESOLVE_PAGE_SIZE = 50
        const val FTS5_TRIGRAM_MIN_LENGTH = 3
    }

    private val conversationsRefreshTrigger = MutableStateFlow(0L)
    private val _typingState = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val _onlineStatus = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    private val syncEvents = Channel<MessagingSyncEvent>(capacity = Channel.BUFFERED)
    private val projector = MessagingLocalProjector(
        conversationDao = conversationDao,
        messageDao = messageDao
    )

    private var activeConversationId: Long? = null

    init {
        repositoryScope.launch { observeNotificationEvents() }
        repositoryScope.launch { processSyncEvents() }
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
    override fun messagesPaging(conversationId: Long): Flow<PagingData<Message>> =
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            remoteMediator = MessageRemoteMediator(
                conversationId = conversationId,
                remoteDataSource = remoteDataSource,
                messageDao = messageDao
            ),
            pagingSourceFactory = { messageDao.getPagingSource(conversationId) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override suspend fun resolveConversationId(otherUserId: Long): Long? {
        if (otherUserId <= 0L) return null

        conversationDao.getByOtherUserId(otherUserId)?.id?.let { return it }

        var offset = 0
        while (true) {
            val conversationList = fold<MessagingError, ConversationList, ConversationList?>(
                block = {
                    remoteDataSource.getConversations(
                        limit = CONVERSATION_RESOLVE_PAGE_SIZE,
                        offset = offset
                    )
                },
                recover = { null },
                transform = { it }
            ) ?: return null

            conversationList.conversations
                .firstOrNull { it.otherUser.id == otherUserId }
                ?.id
                ?.let { return it }

            if (!conversationList.hasMore || conversationList.conversations.isEmpty()) {
                return null
            }

            offset += conversationList.conversations.size
        }
    }

    context(_: Raise<MessagingError>)
    override suspend fun sendMessage(
        recipientId: Long,
        content: String,
        imageUrl: String?,
        replyToMessageId: Long?
    ): Message {
        val message = remoteDataSource.sendMessage(
            recipientId = recipientId,
            content = content,
            imageUrl = imageUrl,
            replyToMessageId = replyToMessageId
        )
        enqueueSyncEvent(MessagingSyncEvent.LocalMessageSent(message))
        return message
    }

    context(_: Raise<MessagingError>)
    override suspend fun deleteMessage(
        messageId: Long
    ) {
        remoteDataSource.deleteMessage(messageId)
        val now = Clock.System.now().toEpochMilliseconds()
        enqueueSyncEvent(
            MessagingSyncEvent.LocalMessageDeleted(
                messageId = messageId,
                deletedAt = now
            )
        )
    }

    context(_: Raise<MessagingError>)
    override suspend fun recallMessage(
        messageId: Long
    ) {
        remoteDataSource.recallMessage(messageId)
        val now = Clock.System.now().toEpochMilliseconds()
        enqueueSyncEvent(
            MessagingSyncEvent.LocalMessageRecalled(
                messageId = messageId,
                recalledAt = now
            )
        )
    }

    context(_: Raise<MessagingError>)
    override suspend fun markAsRead(
        conversationId: Long
    ) = confirmConversationRead(conversationId)

    override fun setActiveConversation(conversationId: Long?) {
        dispatchSyncEvent(MessagingSyncEvent.ActiveConversationChanged(conversationId))
    }

    override fun typingIndicators(conversationId: Long): Flow<Boolean> =
        _typingState.map { it[conversationId] ?: false }

    override fun onlineStatus(): Flow<Map<Long, Boolean>> =
        _onlineStatus.asStateFlow()

    override fun sendTyping(conversationId: Long) {
        repositoryScope.launch { notificationService.sendTyping(conversationId, isTyping = true) }
    }

    override fun sendStopTyping(conversationId: Long) {
        repositoryScope.launch { notificationService.sendTyping(conversationId, isTyping = false) }
    }

    context(_: Raise<MessagingError>)
    override suspend fun searchMessages(
        conversationId: Long,
        query: String
    ): List<MessageSearchItem> = catch({
        if (query.length >= FTS5_TRIGRAM_MIN_LENGTH) {
            val escaped = query.replace("\"", "\"\"")
            messageDao.searchMessages(
                conversationId = conversationId,
                query = "\"$escaped\""
            ).map { it.toDomain() }
        } else {
            messageDao.searchMessagesLike(
                conversationId = conversationId,
                query = query
            ).map { entity ->
                entity.toDomain().let { item ->
                    item.copy(highlightedContent = highlightSubstring(item.message.content, query))
                }
            }
        }
    }) {
        raise(MessagingError.Unknown(it.message ?: "Search failed"))
    }

    private fun highlightSubstring(content: String, query: String): String {
        val sb = StringBuilder()
        var cursor = 0
        val lowerContent = content.lowercase()
        val lowerQuery = query.lowercase()
        while (cursor < content.length) {
            val index = lowerContent.indexOf(lowerQuery, cursor)
            if (index == -1) {
                sb.append(content.substring(cursor))
                break
            }
            sb.append(content.substring(cursor, index))
            sb.append("<mark>")
            sb.append(content.substring(index, index + query.length))
            sb.append("</mark>")
            cursor = index + query.length
        }
        return sb.toString()
    }

    private suspend fun observeNotificationEvents() {
        notificationService.notificationEvents.collect { event ->
            when (event) {
                is NotificationEvent.NewMessage -> enqueueSyncEvent(MessagingSyncEvent.RemoteNewMessage(event))
                is NotificationEvent.MessagesRead -> enqueueSyncEvent(MessagingSyncEvent.RemoteMessagesRead(event))
                is NotificationEvent.MessageRecalled -> enqueueSyncEvent(MessagingSyncEvent.RemoteMessageRecalled(event))
                is NotificationEvent.TypingIndicator -> enqueueSyncEvent(MessagingSyncEvent.RemoteTypingIndicator(event))
                is NotificationEvent.PresenceSnapshot -> enqueueSyncEvent(MessagingSyncEvent.RemotePresenceSnapshot(event))
                is NotificationEvent.UserPresenceChanged -> enqueueSyncEvent(MessagingSyncEvent.RemoteUserPresenceChanged(event))
                else -> Unit
            }
        }
    }

    private suspend fun processSyncEvents() {
        for (event in syncEvents) {
            when (event) {
                is MessagingSyncEvent.ActiveConversationChanged -> {
                    activeConversationId = event.conversationId
                }

                is MessagingSyncEvent.LocalMessageSent -> {
                    handleProjectionResult(projector.projectMessageSent(event.message))
                }

                is MessagingSyncEvent.LocalMessageDeleted -> {
                    projector.projectMessageDeleted(
                        messageId = event.messageId,
                        deletedAt = event.deletedAt
                    )
                }

                is MessagingSyncEvent.LocalMessageRecalled -> {
                    projector.projectMessageRecalled(
                        messageId = event.messageId,
                        recalledAt = event.recalledAt
                    )
                }

                is MessagingSyncEvent.LocalConversationReadConfirmed -> {
                    projector.projectConversationRead(
                        conversationId = event.conversationId,
                        readAt = event.readAt
                    )
                }

                is MessagingSyncEvent.RemoteNewMessage -> {
                    val isActiveConversation = activeConversationId == event.event.conversationId
                    val projection = projector.projectRemoteNewMessage(
                        event = event.event,
                        isActiveConversation = isActiveConversation
                    )
                    handleProjectionResult(projection)

                    if (projection.shouldMarkConversationAsRead) {
                        requestMarkConversationAsRead(event.event.conversationId)
                    }
                }

                is MessagingSyncEvent.RemoteMessagesRead -> {
                    projector.projectRemoteMessagesRead(event.event)
                }

                is MessagingSyncEvent.RemoteMessageRecalled -> {
                    projector.projectMessageRecalled(
                        messageId = event.event.messageId,
                        recalledAt = event.event.timestamp
                    )
                }

                is MessagingSyncEvent.RemoteTypingIndicator -> {
                    _typingState.update { current ->
                        current + (event.event.conversationId to event.event.isTyping)
                    }
                }

                is MessagingSyncEvent.RemotePresenceSnapshot -> {
                    _onlineStatus.update {
                        event.event.users.associate { user -> user.userId to user.isOnline }
                    }
                }

                is MessagingSyncEvent.RemoteUserPresenceChanged -> {
                    _onlineStatus.update { current ->
                        current + (event.event.userId to event.event.isOnline)
                    }
                }
            }
        }
    }

    private fun handleProjectionResult(result: MessagingProjectionResult) {
        if (result.requiresConversationRefresh) {
            conversationsRefreshTrigger.update { it + 1 }
        }
    }

    private fun requestMarkConversationAsRead(conversationId: Long) {
        repositoryScope.launch {
            fold(
                block = { confirmConversationRead(conversationId) },
                recover = {},
                transform = {}
            )
        }
    }

    context(_: Raise<MessagingError>)
    private suspend fun confirmConversationRead(
        conversationId: Long
    ) {
        val readAt = remoteDataSource.markAsRead(conversationId)
        enqueueSyncEvent(
            MessagingSyncEvent.LocalConversationReadConfirmed(
                conversationId = conversationId,
                readAt = readAt
            )
        )
    }

    private suspend fun enqueueSyncEvent(event: MessagingSyncEvent) {
        syncEvents.send(event)
    }

    private fun dispatchSyncEvent(event: MessagingSyncEvent) {
        if (syncEvents.trySend(event).isFailure) {
            repositoryScope.launch {
                syncEvents.send(event)
            }
        }
    }
}
