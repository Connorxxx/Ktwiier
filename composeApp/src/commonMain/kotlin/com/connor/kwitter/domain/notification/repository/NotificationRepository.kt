package com.connor.kwitter.domain.notification.repository

import com.connor.kwitter.domain.notification.model.ConnectionState
import com.connor.kwitter.domain.notification.model.NotificationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NotificationRepository {
    val newPostEvents: Flow<NotificationEvent.NewPostCreated>
    val connectionState: StateFlow<ConnectionState>
    fun observePostLikedEvents(postId: Long): Flow<NotificationEvent.PostLiked>
}

