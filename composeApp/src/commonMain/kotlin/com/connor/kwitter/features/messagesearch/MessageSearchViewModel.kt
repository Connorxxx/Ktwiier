package com.connor.kwitter.features.messagesearch

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
import arrow.core.raise.fold
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface MessageSearchScreenState {
    data class Empty(val submittedQuery: String? = null) : MessageSearchScreenState
    data class Loading(val submittedQuery: String) : MessageSearchScreenState
    data class Content(
        val items: List<MessageSearchItem>,
        val submittedQuery: String
    ) : MessageSearchScreenState
    data class Error(
        val message: String,
        val submittedQuery: String,
        val canRetry: Boolean = true
    ) : MessageSearchScreenState
}

data class MessageSearchUiState(
    val conversationId: Long = 0L,
    val otherUserDisplayName: String = "",
    val query: String = "",
    val screenState: MessageSearchScreenState = MessageSearchScreenState.Empty()
)

sealed interface MessageSearchIntent

sealed interface MessageSearchAction : MessageSearchIntent {
    data class Load(
        val conversationId: Long,
        val otherUserDisplayName: String
    ) : MessageSearchAction
    data class UpdateQuery(val text: String) : MessageSearchAction
    data object SubmitSearch : MessageSearchAction
    data object ErrorDismissed : MessageSearchAction
}

sealed interface MessageSearchNavAction : MessageSearchIntent {
    data object BackClick : MessageSearchNavAction
    data class ResultClick(val messageId: Long) : MessageSearchNavAction
}

class MessageSearchViewModel(
    private val messagingRepository: MessagingRepository
) : ViewModel() {

    private val _events = Channel<MessageSearchAction>(Channel.UNLIMITED)

    val uiState: StateFlow<MessageSearchUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        MessageSearchPresenter()
    }

    fun onEvent(event: MessageSearchAction) {
        _events.trySend(event)
    }

    @Composable
    private fun MessageSearchPresenter(): MessageSearchUiState {
        var state by remember { mutableStateOf(MessageSearchUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is MessageSearchAction.Load -> state.copy(
                        conversationId = action.conversationId,
                        otherUserDisplayName = action.otherUserDisplayName,
                        query = "",
                        screenState = MessageSearchScreenState.Empty()
                    )

                    is MessageSearchAction.UpdateQuery -> state.copy(
                        query = action.text
                    )

                    is MessageSearchAction.SubmitSearch -> {
                        val query = state.query.trim()
                        if (query.isEmpty()) {
                            state.copy(screenState = MessageSearchScreenState.Empty())
                        } else {
                            state = state.copy(
                                screenState = MessageSearchScreenState.Loading(submittedQuery = query)
                            )
                            performSearch(state, query)
                        }
                    }

                    is MessageSearchAction.ErrorDismissed -> {
                        val currentScreen = state.screenState
                        if (currentScreen is MessageSearchScreenState.Error) {
                            state.copy(screenState = MessageSearchScreenState.Empty())
                        } else {
                            state
                        }
                    }
                }
            }
        }

        return state
    }

    private suspend fun performSearch(
        currentState: MessageSearchUiState,
        query: String
    ): MessageSearchUiState {
        return fold(
            block = {
                messagingRepository.searchMessages(
                    conversationId = currentState.conversationId,
                    query = query
                )
            },
            recover = { error ->
                currentState.copy(
                    screenState = MessageSearchScreenState.Error(
                        message = formatError(error),
                        submittedQuery = query
                    )
                )
            },
            transform = { results ->
                currentState.copy(
                    screenState = if (results.isEmpty()) {
                        MessageSearchScreenState.Empty(submittedQuery = query)
                    } else {
                        MessageSearchScreenState.Content(
                            items = results,
                            submittedQuery = query
                        )
                    }
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
