package com.connor.kwitter.features.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

sealed interface ConversationListIntent

sealed interface ConversationListAction : ConversationListIntent

sealed interface ConversationListNavAction : ConversationListIntent {
    data object BackClick : ConversationListNavAction
    data class ConversationClick(
        val conversationId: String,
        val otherUserId: String,
        val otherUserDisplayName: String,
        val otherUserAvatarUrl: String?
    ) : ConversationListNavAction
}

class ConversationListViewModel(
    messagingRepository: MessagingRepository
) : ViewModel() {

    val pagingFlow: Flow<PagingData<Conversation>> = messagingRepository
        .conversationsPaging
        .cachedIn(viewModelScope)

    val onlineStatus: StateFlow<Map<String, Boolean>> = messagingRepository
        .onlineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
