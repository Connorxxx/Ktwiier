package com.connor.kwitter.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class ChatUiState(
    val conversationId: String? = null,
    val otherUserId: String = "",
    val otherUserDisplayName: String = "",
    val currentUserId: String? = null,
    val messages: List<Message> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val messageInput: String = "",
    val error: String? = null
)

sealed interface ChatIntent

sealed interface ChatAction : ChatIntent {
    data class Load(
        val conversationId: String?,
        val otherUserId: String,
        val otherUserDisplayName: String
    ) : ChatAction
    data object LoadMore : ChatAction
    data class UpdateMessageInput(val text: String) : ChatAction
    data object SendMessage : ChatAction
    data object ErrorDismissed : ChatAction
}

sealed interface ChatNavAction : ChatIntent {
    data object BackClick : ChatNavAction
}

class ChatViewModel(
    private val messagingRepository: MessagingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 50
    }

    private val _events = Channel<ChatAction>(Channel.UNLIMITED)

    val uiState: StateFlow<ChatUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        ChatPresenter()
    }

    fun onEvent(event: ChatAction) {
        _events.trySend(event)
    }

    @Composable
    private fun ChatPresenter(): ChatUiState {
        var state by remember { mutableStateOf(ChatUiState()) }
        val currentUserId by authRepository.currentUserId.collectAsState(initial = null)

        LaunchedEffect(Unit) {
            messagingRepository.newMessageEvents.collect { event ->
                if (event.conversationId == state.conversationId) {
                    // Append new message to the end (newest)
                    val newMessage = Message(
                        id = event.messageId,
                        conversationId = event.conversationId,
                        senderId = "", // sender is the other user
                        content = event.contentPreview,
                        imageUrl = null,
                        readAt = null,
                        createdAt = event.timestamp
                    )
                    state = state.copy(
                        messages = state.messages + newMessage
                    )
                    // Auto mark as read
                    state.conversationId?.let { convId ->
                        messagingRepository.markAsRead(convId)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            messagingRepository.messagesReadEvents.collect { event ->
                if (event.conversationId == state.conversationId) {
                    // Update readAt on sent messages
                    state = state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.senderId == currentUserId && msg.readAt == null) {
                                msg.copy(readAt = event.timestamp)
                            } else msg
                        }
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is ChatAction.Load -> load(action, state)
                    is ChatAction.LoadMore -> loadMore(state)
                    is ChatAction.UpdateMessageInput -> state.copy(messageInput = action.text)
                    is ChatAction.SendMessage -> sendMessage(state)
                    is ChatAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state.copy(currentUserId = currentUserId)
    }

    private suspend fun load(
        action: ChatAction.Load,
        currentState: ChatUiState
    ): ChatUiState {
        val loadingState = currentState.copy(
            conversationId = action.conversationId,
            otherUserId = action.otherUserId,
            otherUserDisplayName = action.otherUserDisplayName,
            isLoading = true,
            error = null
        )

        // No conversationId means new DM from profile — start empty
        if (action.conversationId == null) {
            return loadingState.copy(isLoading = false)
        }

        val result = messagingRepository.getMessages(action.conversationId, PAGE_SIZE, 0)
        val stateAfterLoad = result.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoading = false,
                    error = formatError(error)
                )
            },
            ifRight = { messageList ->
                loadingState.copy(
                    isLoading = false,
                    // API returns DESC, reverse to oldest-first for display
                    messages = messageList.messages.reversed(),
                    hasMore = messageList.hasMore
                )
            }
        )

        // Mark as read
        messagingRepository.markAsRead(action.conversationId)

        return stateAfterLoad
    }

    private suspend fun loadMore(currentState: ChatUiState): ChatUiState {
        val conversationId = currentState.conversationId ?: return currentState
        if (currentState.isLoadingMore || !currentState.hasMore) return currentState

        val loadingState = currentState.copy(isLoadingMore = true)
        val offset = currentState.messages.size

        return messagingRepository.getMessages(conversationId, PAGE_SIZE, offset).fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingMore = false,
                    error = formatError(error)
                )
            },
            ifRight = { messageList ->
                loadingState.copy(
                    isLoadingMore = false,
                    // Prepend older messages (reversed from DESC)
                    messages = messageList.messages.reversed() + currentState.messages,
                    hasMore = messageList.hasMore
                )
            }
        )
    }

    private suspend fun sendMessage(currentState: ChatUiState): ChatUiState {
        val content = currentState.messageInput.trim()
        if (content.isBlank() || currentState.isSending) return currentState

        val sendingState = currentState.copy(isSending = true, error = null)

        return messagingRepository.sendMessage(
            recipientId = currentState.otherUserId,
            content = content
        ).fold(
            ifLeft = { error ->
                sendingState.copy(
                    isSending = false,
                    error = formatError(error)
                )
            },
            ifRight = { message ->
                sendingState.copy(
                    isSending = false,
                    messageInput = "",
                    // Update conversationId if this was the first message
                    conversationId = message.conversationId,
                    messages = currentState.messages + message
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
