package com.connor.kwitter.domain.notification.repository

import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NotificationRepository {
    val newPostEvents: Flow<NotificationEvent.NewPostCreated>
    val postLikedEvents: Flow<NotificationEvent.PostLiked>
    val connectionState: StateFlow<ConnectionState>
    suspend fun subscribeToPost(postId: String)
    suspend fun unsubscribeFromPost(postId: String)
}
