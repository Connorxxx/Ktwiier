package com.connor.kwitter.features.conversationlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.connor.kwitter.domain.messaging.model.Conversation
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed interface ConversationListIntent

sealed interface ConversationListAction : ConversationListIntent {
    data object Load : ConversationListAction
    data object Refresh : ConversationListAction
    data object LoadMore : ConversationListAction
    data object ErrorDismissed : ConversationListAction
}

sealed interface ConversationListNavAction : ConversationListIntent {
    data object BackClick : ConversationListNavAction
    data class ConversationClick(
        val conversationId: String,
        val otherUserId: String,
        val otherUserDisplayName: String
    ) : ConversationListNavAction
}

class ConversationListViewModel(
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _events = Channel<ConversationListAction>(Channel.UNLIMITED)

    val uiState: StateFlow<ConversationListUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        ConversationListPresenter()
    }

    fun onEvent(event: ConversationListAction) {
        _events.trySend(event)
    }

    @Composable
    private fun ConversationListPresenter(): ConversationListUiState {
        var state by remember { mutableStateOf(ConversationListUiState()) }

        LaunchedEffect(Unit) {
            messagingRepository.newMessageEvents.collect { event ->
                // Bump conversation with new message to top, increment unread
                val existing = state.conversations.find { it.id == event.conversationId }
                if (existing != null) {
                    val updated = existing.copy(
                        unreadCount = existing.unreadCount + 1,
                        lastMessage = existing.lastMessage?.copy(
                            content = event.contentPreview,
                            createdAt = event.timestamp
                        ) ?: com.connor.kwitter.domain.messaging.model.Message(
                            id = event.messageId,
                            conversationId = event.conversationId,
                            senderId = "",
                            content = event.contentPreview,
                            imageUrl = null,
                            readAt = null,
                            createdAt = event.timestamp
                        )
                    )
                    state = state.copy(
                        conversations = listOf(updated) + state.conversations.filter { it.id != event.conversationId }
                    )
                } else {
                    // New conversation not in list yet — reload
                    _events.trySend(ConversationListAction.Refresh)
                }
            }
        }

        LaunchedEffect(Unit) {
            messagingRepository.messagesReadEvents.collect { event ->
                state = state.copy(
                    conversations = state.conversations.map { conv ->
                        if (conv.id == event.conversationId) {
                            conv.copy(unreadCount = 0)
                        } else conv
                    }
                )
            }
        }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is ConversationListAction.Load -> load(state)
                    is ConversationListAction.Refresh -> refresh(state)
                    is ConversationListAction.LoadMore -> loadMore(state)
                    is ConversationListAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun load(currentState: ConversationListUiState): ConversationListUiState {
        val loadingState = currentState.copy(isLoading = true, error = null)

        return messagingRepository.getConversations(PAGE_SIZE, 0).fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoading = false,
                    error = formatError(error)
                )
            },
            ifRight = { conversationList ->
                loadingState.copy(
                    isLoading = false,
                    conversations = conversationList.conversations,
                    hasMore = conversationList.hasMore
                )
            }
        )
    }

    private suspend fun refresh(currentState: ConversationListUiState): ConversationListUiState {
        val refreshingState = currentState.copy(isRefreshing = true, error = null)

        return messagingRepository.getConversations(PAGE_SIZE, 0).fold(
            ifLeft = { error ->
                refreshingState.copy(
                    isRefreshing = false,
                    error = formatError(error)
                )
            },
            ifRight = { conversationList ->
                refreshingState.copy(
                    isRefreshing = false,
                    conversations = conversationList.conversations,
                    hasMore = conversationList.hasMore
                )
            }
        )
    }

    private suspend fun loadMore(currentState: ConversationListUiState): ConversationListUiState {
        if (currentState.isLoadingMore || !currentState.hasMore) return currentState

        val loadingState = currentState.copy(isLoadingMore = true)
        val offset = currentState.conversations.size

        return messagingRepository.getConversations(PAGE_SIZE, offset).fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingMore = false,
                    error = formatError(error)
                )
            },
            ifRight = { conversationList ->
                loadingState.copy(
                    isLoadingMore = false,
                    conversations = currentState.conversations + conversationList.conversations,
                    hasMore = conversationList.hasMore
                )
            }
        )
    }

    private fun formatError(error: MessagingError): String = when (error) {
        is MessagingError.NetworkError -> "Network error: ${error.message}"
        is MessagingError.ServerError -> "Server error (${error.code}): ${error.message}"
        is MessagingError.ClientError -> "Request error (${error.code}): ${error.message}"
        is MessagingError.Unauthorized -> "Authentication required"
        is MessagingError.NotFound -> "Not found"
        is MessagingError.Unknown -> "Unknown error: ${error.message}"
    }
}
