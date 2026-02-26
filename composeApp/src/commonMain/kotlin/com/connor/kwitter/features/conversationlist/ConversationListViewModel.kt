package com.connor.kwitter.features.conversationlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConversationListIntent

sealed interface ConversationListAction : ConversationListIntent

sealed interface ConversationListNavAction : ConversationListIntent {
    data object BackClick : ConversationListNavAction
    data class ConversationClick(
        val conversationId: Long,
        val otherUserId: Long,
        val otherUserDisplayName: String,
        val otherUserAvatarUrl: String?
    ) : ConversationListNavAction
}

data class ConversationListUiState(
    val onlineStatus: Map<Long, Boolean> = emptyMap()
)

class ConversationListViewModel(
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    val pagingFlow: Flow<PagingData<Conversation>> = messagingRepository
        .conversationsPaging
        .cachedIn(viewModelScope)

    val uiState: StateFlow<ConversationListUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        ConversationListPresenter()
    }

    @Composable
    private fun ConversationListPresenter(): ConversationListUiState {
        val onlineStatus by messagingRepository
            .onlineStatus()
            .collectAsState(initial = emptyMap())

        return ConversationListUiState(onlineStatus = onlineStatus)
    }
}


