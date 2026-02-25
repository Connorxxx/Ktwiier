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
import com.connor.kwitter.domain.messaging.model.MessageSearchItem
import com.connor.kwitter.domain.messaging.model.MessagingError
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class MessageSearchUiState(
    val conversationId: String = "",
    val otherUserDisplayName: String = "",
    val query: String = "",
    val results: List<MessageSearchItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

sealed interface MessageSearchIntent

sealed interface MessageSearchAction : MessageSearchIntent {
    data class Load(
        val conversationId: String,
        val otherUserDisplayName: String
    ) : MessageSearchAction
    data class UpdateQuery(val text: String) : MessageSearchAction
    data object SubmitSearch : MessageSearchAction
    data object ErrorDismissed : MessageSearchAction
}

sealed interface MessageSearchNavAction : MessageSearchIntent {
    data object BackClick : MessageSearchNavAction
    data class ResultClick(val messageId: String) : MessageSearchNavAction
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
                        otherUserDisplayName = action.otherUserDisplayName
                    )

                    is MessageSearchAction.UpdateQuery -> state.copy(
                        query = action.text
                    )

                    is MessageSearchAction.SubmitSearch -> {
                        val query = state.query.trim()
                        if (query.isEmpty()) {
                            state.copy(hasSearched = false)
                        } else {
                            performSearch(state, query)
                        }
                    }

                    is MessageSearchAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun performSearch(
        currentState: MessageSearchUiState,
        query: String
    ): MessageSearchUiState {
        val searchingState = currentState.copy(isSearching = true, error = null)

        return messagingRepository.searchMessages(
            conversationId = currentState.conversationId,
            query = query
        ).fold(
            ifLeft = { error ->
                searchingState.copy(
                    isSearching = false,
                    hasSearched = true,
                    error = formatError(error)
                )
            },
            ifRight = { results ->
                searchingState.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = results
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
