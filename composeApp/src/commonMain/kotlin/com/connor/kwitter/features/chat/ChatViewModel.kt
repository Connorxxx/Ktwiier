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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import arrow.core.raise.fold
import com.connor.kwitter.core.result.Result
import com.connor.kwitter.core.result.uiResultOf
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: Long? = null,
    val otherUserId: Long = 0L,
    val otherUserDisplayName: String = "",
    val otherUserAvatarUrl: String? = null,
    val currentUserId: Long? = null,
    val isSending: Boolean = false,
    val messageInput: String = "",
    val error: String? = null,
    val isOtherUserTyping: Boolean = false,
    val replyingToMessage: Message? = null
) {
    val operationResult: Result<Unit, String>
        get() = uiResultOf(isLoading = isSending, error = error)
}

sealed interface ChatIntent

sealed interface ChatAction : ChatIntent {
    data class Load(
        val conversationId: Long?,
        val otherUserId: Long,
        val otherUserDisplayName: String,
        val otherUserAvatarUrl: String?
    ) : ChatAction
    data class UpdateMessageInput(val text: String) : ChatAction
    data object SendMessage : ChatAction
    data object ErrorDismissed : ChatAction
    data class DeleteMessage(val messageId: Long) : ChatAction
    data class RecallMessage(val messageId: Long) : ChatAction
    data class StartReply(val message: Message) : ChatAction
    data object CancelReply : ChatAction
    data object ScreenDisposed : ChatAction
}

sealed interface ChatNavAction : ChatIntent {
    data object BackClick : ChatNavAction
    data class UserProfileClick(val userId: Long) : ChatNavAction
    data object SearchClick : ChatNavAction
}

class ChatViewModel(
    private val messagingRepository: MessagingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<ChatAction>(Channel.UNLIMITED)
    private val _conversationId = MutableStateFlow<Long?>(null)
    private var typingJob: Job? = null

    companion object {
        private const val TYPING_DEBOUNCE_MS = 3000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagingFlow: Flow<PagingData<Message>> = _conversationId
        .flatMapLatest { convId ->
            if (convId != null) {
                messagingRepository.messagesPaging(convId).cachedIn(viewModelScope)
            } else {
                flowOf(PagingData.empty())
            }
        }

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
        val conversationId = state.conversationId

        // Observe typing indicators for current conversation
        val isOtherUserTyping by remember(conversationId) {
            if (conversationId != null) {
                messagingRepository.typingIndicators(conversationId)
            } else {
                flowOf(false)
            }
        }.collectAsState(initial = false)

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is ChatAction.Load -> {
                        val resolvedConversationId = action.conversationId
                            ?: messagingRepository.resolveConversationId(action.otherUserId)
                            ?: state.takeIf { it.otherUserId == action.otherUserId }?.conversationId
                        messagingRepository.setActiveConversation(resolvedConversationId)
                        _conversationId.value = resolvedConversationId
                        // Mark as read if entering existing conversation
                        resolvedConversationId?.let {
                            fold(
                                block = { messagingRepository.markAsRead(it) },
                                recover = {},
                                transform = {}
                            )
                        }
                        state.copy(
                            conversationId = resolvedConversationId,
                            otherUserId = action.otherUserId,
                            otherUserDisplayName = action.otherUserDisplayName,
                            otherUserAvatarUrl = action.otherUserAvatarUrl,
                            error = null
                        )
                    }
                    is ChatAction.UpdateMessageInput -> {
                        handleTypingDebounce(state.conversationId)
                        state.copy(messageInput = action.text)
                    }
                    is ChatAction.SendMessage -> sendMessage(state)
                    is ChatAction.ErrorDismissed -> state.copy(error = null)
                    is ChatAction.DeleteMessage -> {
                        deleteMessage(state, action.messageId)
                    }
                    is ChatAction.RecallMessage -> {
                        recallMessage(state, action.messageId)
                    }
                    is ChatAction.StartReply -> state.copy(replyingToMessage = action.message)
                    is ChatAction.CancelReply -> state.copy(replyingToMessage = null)
                    ChatAction.ScreenDisposed -> {
                        typingJob?.cancel()
                        state.conversationId?.let { messagingRepository.sendStopTyping(it) }
                        messagingRepository.setActiveConversation(null)
                        _conversationId.value = null
                        state
                    }
                }
            }
        }

        return state.copy(
            currentUserId = currentUserId,
            isOtherUserTyping = isOtherUserTyping
        )
    }

    private fun handleTypingDebounce(conversationId: Long?) {
        conversationId ?: return
        typingJob?.cancel()
        messagingRepository.sendTyping(conversationId)
        typingJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            messagingRepository.sendStopTyping(conversationId)
        }
    }

    private suspend fun sendMessage(currentState: ChatUiState): ChatUiState {
        val content = currentState.messageInput.trim()
        if (content.isBlank() || currentState.isSending) return currentState

        val sendingState = currentState.copy(isSending = true, error = null)

        // Stop typing indicator on send
        typingJob?.cancel()
        currentState.conversationId?.let { messagingRepository.sendStopTyping(it) }

        return fold(
            block = {
                messagingRepository.sendMessage(
                    recipientId = currentState.otherUserId,
                    content = content,
                    replyToMessageId = currentState.replyingToMessage?.id
                )
            },
            recover = { error ->
                sendingState.copy(
                    isSending = false,
                    error = formatError(error)
                )
            },
            transform = { message ->
                // If this was the first message, update conversationId so paging starts
                if (currentState.conversationId == null) {
                    _conversationId.value = message.conversationId
                    messagingRepository.setActiveConversation(message.conversationId)
                }
                sendingState.copy(
                    isSending = false,
                    messageInput = "",
                    conversationId = message.conversationId,
                    replyingToMessage = null
                )
            }
        )
    }

    private suspend fun deleteMessage(currentState: ChatUiState, messageId: Long): ChatUiState {
        return fold(
            block = { messagingRepository.deleteMessage(messageId) },
            recover = { error -> currentState.copy(error = formatError(error)) },
            transform = { currentState }
        )
    }

    private suspend fun recallMessage(currentState: ChatUiState, messageId: Long): ChatUiState {
        return fold(
            block = { messagingRepository.recallMessage(messageId) },
            recover = { error -> currentState.copy(error = formatError(error)) },
            transform = { currentState }
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

    override fun onCleared() {
        typingJob?.cancel()
        messagingRepository.setActiveConversation(null)
        super.onCleared()
    }
}


