package com.connor.kwitter.features.postdetail

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
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class PostDetailUiState(
    val post: Post? = null,
    val replies: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface PostDetailIntent

sealed interface PostDetailAction : PostDetailIntent {
    data class Load(val postId: String) : PostDetailAction
    data class Refresh(val postId: String) : PostDetailAction
    data object ErrorDismissed : PostDetailAction
}

sealed interface PostDetailNavAction : PostDetailIntent {
    data class ReplyClick(val postId: String) : PostDetailNavAction
    data object BackClick : PostDetailNavAction
}

class PostDetailViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    private val _events = Channel<PostDetailAction>(Channel.UNLIMITED)

    val uiState: StateFlow<PostDetailUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        PostDetailPresenter()
    }

    fun onEvent(event: PostDetailAction) {
        _events.trySend(event)
    }

    @Composable
    private fun PostDetailPresenter(): PostDetailUiState {
        var state by remember { mutableStateOf(PostDetailUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is PostDetailAction.Load -> {
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading
                        val postResult = postRepository.getPost(action.postId)
                        val repliesResult = postRepository.getReplies(action.postId)
                        postResult.fold(
                            ifLeft = { error ->
                                loading.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = { post ->
                                val replies = repliesResult.getOrNull()?.posts ?: emptyList()
                                loading.copy(isLoading = false, post = post, replies = replies)
                            }
                        )
                    }
                    is PostDetailAction.Refresh -> {
                        val refreshing = state.copy(isLoading = true, error = null)
                        state = refreshing
                        val postResult = postRepository.getPost(action.postId)
                        val repliesResult = postRepository.getReplies(action.postId)
                        postResult.fold(
                            ifLeft = { error ->
                                refreshing.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = { post ->
                                val replies = repliesResult.getOrNull()?.posts ?: emptyList()
                                refreshing.copy(isLoading = false, post = post, replies = replies)
                            }
                        )
                    }
                    is PostDetailAction.ErrorDismissed -> state.copy(error = null)
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
        is PostError.NotFound -> "Post not found"
        is PostError.Unknown -> "Unknown error: ${error.message}"
    }
}
