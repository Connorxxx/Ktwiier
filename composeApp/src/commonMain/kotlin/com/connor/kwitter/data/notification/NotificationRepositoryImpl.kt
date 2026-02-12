package com.connor.kwitter.data.notification

import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import com.connor.kwitter.domain.notification.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance

class NotificationRepositoryImpl(
    private val notificationService: NotificationService
) : NotificationRepository {

    override val newPostEvents: Flow<NotificationEvent.NewPostCreated> =
        notificationService.notificationEvents.filterIsInstance()

    override val postLikedEvents: Flow<NotificationEvent.PostLiked> =
        notificationService.notificationEvents.filterIsInstance()

    override val connectionState: StateFlow<ConnectionState> =
        notificationService.connectionState

    override suspend fun subscribeToPost(postId: String) {
        notificationService.sendSubscribePost(postId)
    }

    override suspend fun unsubscribeFromPost(postId: String) {
        notificationService.sendUnsubscribePost(postId)
    }
}
