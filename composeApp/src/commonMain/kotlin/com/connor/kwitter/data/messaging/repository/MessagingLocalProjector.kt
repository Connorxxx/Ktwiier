package com.connor.kwitter.data.messaging.repository

import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.MessageDao
import com.connor.kwitter.data.messaging.local.toEntity
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.notification.model.NotificationEvent

internal data class MessagingProjectionResult(
    val requiresConversationRefresh: Boolean = false,
    val shouldMarkConversationAsRead: Boolean = false
)

internal class MessagingLocalProjector(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    suspend fun projectMessageSent(message: Message): MessagingProjectionResult {
        val minMessageIndex = messageDao.getMinOrderIndex(message.conversationId) ?: 0
        messageDao.insert(message.toEntity(orderIndex = minMessageIndex - 1))

        val existingConversation = conversationDao.getById(message.conversationId)
            ?: return MessagingProjectionResult(requiresConversationRefresh = true)

        val minConversationIndex = conversationDao.getMinOrderIndex() ?: 0
        conversationDao.insertOrReplace(
            existingConversation.copy(
                lastMessageId = message.id,
                lastMessageContent = message.content,
                lastMessageSenderId = message.senderId,
                lastMessageReadAt = message.readAt,
                lastMessageCreatedAt = message.createdAt,
                lastMessageDeletedAt = message.deletedAt,
                lastMessageRecalledAt = message.recalledAt,
                orderIndex = minConversationIndex - 1
            )
        )

        return MessagingProjectionResult()
    }

    suspend fun projectMessageDeleted(messageId: String, deletedAt: Long) {
        messageDao.markMessageAsDeleted(messageId, deletedAt)
        conversationDao.updateLastMessageDeleted(messageId, deletedAt)
    }

    suspend fun projectMessageRecalled(messageId: String, recalledAt: Long) {
        messageDao.markMessageAsRecalled(messageId, recalledAt)
        conversationDao.updateLastMessageRecalled(messageId, recalledAt)
    }

    suspend fun projectConversationRead(conversationId: String, readAt: Long) {
        conversationDao.updateUnreadCount(conversationId, 0)

        val conversation = conversationDao.getById(conversationId) ?: return
        messageDao.markMessagesAsReadFromSender(
            conversationId = conversationId,
            senderId = conversation.otherUserId,
            readAt = readAt
        )

        if (conversation.lastMessageSenderId == conversation.otherUserId) {
            conversationDao.insertOrReplace(
                conversation.copy(lastMessageReadAt = readAt)
            )
        }
    }

    suspend fun projectRemoteNewMessage(
        event: NotificationEvent.NewMessage,
        isActiveConversation: Boolean
    ): MessagingProjectionResult {
        val existingConversation = conversationDao.getById(event.conversationId)
        val senderId = existingConversation?.otherUserId.orEmpty()

        val incomingMessage = Message(
            id = event.messageId,
            conversationId = event.conversationId,
            senderId = senderId,
            content = event.contentPreview,
            imageUrl = null,
            readAt = null,
            createdAt = event.timestamp
        )

        val minMessageIndex = messageDao.getMinOrderIndex(event.conversationId) ?: 0
        messageDao.insert(incomingMessage.toEntity(orderIndex = minMessageIndex - 1))

        if (existingConversation == null) {
            return MessagingProjectionResult(
                requiresConversationRefresh = true,
                shouldMarkConversationAsRead = isActiveConversation
            )
        }

        val minConversationIndex = conversationDao.getMinOrderIndex() ?: 0
        conversationDao.insertOrReplace(
            existingConversation.copy(
                lastMessageId = event.messageId,
                lastMessageContent = event.contentPreview,
                lastMessageSenderId = existingConversation.otherUserId,
                lastMessageReadAt = null,
                lastMessageCreatedAt = event.timestamp,
                lastMessageDeletedAt = null,
                lastMessageRecalledAt = null,
                unreadCount = if (isActiveConversation) {
                    existingConversation.unreadCount
                } else {
                    existingConversation.unreadCount + 1
                },
                orderIndex = minConversationIndex - 1
            )
        )

        return MessagingProjectionResult(
            shouldMarkConversationAsRead = isActiveConversation
        )
    }

    suspend fun projectRemoteMessagesRead(event: NotificationEvent.MessagesRead) {
        messageDao.markOutgoingMessagesAsReadByPeer(
            conversationId = event.conversationId,
            readByUserId = event.readByUserId,
            readAt = event.timestamp
        )

        val conversation = conversationDao.getById(event.conversationId) ?: return
        val lastMessageSenderId = conversation.lastMessageSenderId ?: return
        if (lastMessageSenderId != event.readByUserId) {
            conversationDao.insertOrReplace(
                conversation.copy(lastMessageReadAt = event.timestamp)
            )
        }
    }
}
