package com.connor.kwitter.data.messaging.repository

import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.notification.model.NotificationEvent

internal sealed interface MessagingSyncEvent {
    data class ActiveConversationChanged(
        val conversationId: Long?
    ) : MessagingSyncEvent

    data class LocalMessageSent(
        val message: Message
    ) : MessagingSyncEvent

    data class LocalMessageDeleted(
        val messageId: Long,
        val deletedAt: Long
    ) : MessagingSyncEvent

    data class LocalMessageRecalled(
        val messageId: Long,
        val recalledAt: Long
    ) : MessagingSyncEvent

    data class LocalConversationReadConfirmed(
        val conversationId: Long,
        val readAt: Long
    ) : MessagingSyncEvent

    data class RemoteNewMessage(
        val event: NotificationEvent.NewMessage
    ) : MessagingSyncEvent

    data class RemoteMessagesRead(
        val event: NotificationEvent.MessagesRead
    ) : MessagingSyncEvent

    data class RemoteMessageRecalled(
        val event: NotificationEvent.MessageRecalled
    ) : MessagingSyncEvent

    data class RemoteTypingIndicator(
        val event: NotificationEvent.TypingIndicator
    ) : MessagingSyncEvent

    data class RemotePresenceSnapshot(
        val event: NotificationEvent.PresenceSnapshot
    ) : MessagingSyncEvent

    data class RemoteUserPresenceChanged(
        val event: NotificationEvent.UserPresenceChanged
    ) : MessagingSyncEvent
}

