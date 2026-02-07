package com.connor.kwitter.features.createpost

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
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CreatePostUiState(
    val content: String = "",
    val parentId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

sealed interface CreatePostIntent

sealed interface CreatePostAction : CreatePostIntent {
    data class ContentChanged(val content: String) : CreatePostAction
    data class SetParentId(val parentId: String?) : CreatePostAction
    data object SubmitClicked : CreatePostAction
    data object ErrorDismissed : CreatePostAction
}

sealed interface CreatePostNavAction : CreatePostIntent {
    data object OnPostCreated : CreatePostNavAction
    data object BackClick : CreatePostNavAction
}

class CreatePostViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    private val _events = Channel<CreatePostAction>(Channel.UNLIMITED)

    val uiState: StateFlow<CreatePostUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        CreatePostPresenter()
    }

    fun onEvent(event: CreatePostAction) {
        _events.trySend(event)
    }

    @Composable
    private fun CreatePostPresenter(): CreatePostUiState {
        var state by remember { mutableStateOf(CreatePostUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is CreatePostAction.ContentChanged -> state.copy(content = action.content)
                    is CreatePostAction.SetParentId -> state.copy(parentId = action.parentId)
                    is CreatePostAction.SubmitClicked -> {
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading
                        val result = postRepository.createPost(
                            CreatePostRequest(
                                content = loading.content,
                                parentId = loading.parentId
                            )
                        )
                        result.fold(
                            ifLeft = { error ->
                                loading.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = {
                                loading.copy(isLoading = false, isSuccess = true)
                            }
                        )
                    }
                    is CreatePostAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: PostError): String = when (error) {
        is PostError.NetworkError -> "Network error: ${error.message}"
        is PostError.ServerError -> "Server error (${error.code}): ${error.message}"
        is PostError.ClientError -> "Request error (${error.code}): ${error.message}"
        is PostError.Unauthorized -> "Authentication required"
        is PostError.NotFound -> "Not found"
        is PostError.Unknown -> "Unknown error: ${error.message}"
    }
}
