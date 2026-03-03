package com.connor.kwitter.data.notification

import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import com.connor.kwitter.domain.notification.repository.NotificationRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class NotificationRepositoryImpl(
    private val notificationService: NotificationService
) : NotificationRepository {

    override val newPostEvents: Flow<NotificationEvent.NewPostCreated> =
        notificationService.notificationEvents.filterIsInstance()

    override val connectionState: StateFlow<ConnectionState> =
        notificationService.connectionState

    override fun observePostLikeEvents(postId: Long): Flow<NotificationEvent.PostLikeChanged> =
        notificationService.notificationEvents
            .onStart { notificationService.subscribeToPost(postId) }
            .onCompletion {
                withContext(NonCancellable) {
                    notificationService.unsubscribeFromPost(postId)
                }
            }.filterIsInstance()
}
