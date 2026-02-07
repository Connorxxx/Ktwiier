package com.connor.kwitter.features.home

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
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class HomeUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed interface HomeIntent

sealed interface HomeAction : HomeIntent {
    data object LoadTimeline : HomeAction
    data object Refresh : HomeAction
    data object LogoutClick : HomeAction
    data object ErrorDismissed : HomeAction
}

sealed interface HomeNavAction : HomeIntent {
    data class PostClick(val postId: String) : HomeNavAction
    data object CreatePostClick : HomeNavAction
}

class HomeViewModel(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<HomeAction>(Channel.UNLIMITED)

    val uiState: StateFlow<HomeUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        HomePresenter()
    }

    fun onEvent(event: HomeAction) {
        _events.trySend(event)
    }

    @Composable
    private fun HomePresenter(): HomeUiState {
        var state by remember { mutableStateOf(HomeUiState()) }

        LaunchedEffect(Unit) {
            // Auto-load timeline
            state = state.copy(isLoading = true)
            val result = postRepository.getTimeline()
            state = result.fold(
                ifLeft = { error -> state.copy(isLoading = false, error = formatError(error)) },
                ifRight = { postList -> state.copy(isLoading = false, posts = postList.posts) }
            )

            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is HomeAction.LoadTimeline -> {
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading
                        val loadResult = postRepository.getTimeline()
                        loadResult.fold(
                            ifLeft = { error -> loading.copy(isLoading = false, error = formatError(error)) },
                            ifRight = { postList ->
                                loading.copy(isLoading = false, posts = postList.posts)
                            }
                        )
                    }
                    is HomeAction.Refresh -> {
                        val refreshing = state.copy(isRefreshing = true, error = null)
                        state = refreshing
                        val refreshResult = postRepository.getTimeline()
                        refreshResult.fold(
                            ifLeft = { error -> refreshing.copy(isRefreshing = false, error = formatError(error)) },
                            ifRight = { postList ->
                                refreshing.copy(isRefreshing = false, posts = postList.posts)
                            }
                        )
                    }
                    is HomeAction.LogoutClick -> {
                        val current = state.copy(error = null)
                        state = current

                        val logoutResult = authRepository.clearToken()
                        logoutResult.fold(
                            ifLeft = { error ->
                                current.copy(error = formatAuthError(error))
                            },
                            ifRight = { current }
                        )
                    }
                    is HomeAction.ErrorDismissed -> state.copy(error = null)
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

    private fun formatAuthError(error: AuthError): String = when (error) {
        is AuthError.NetworkError -> "Logout failed: network error (${error.message})"
        is AuthError.ServerError -> "Logout failed: server error (${error.code}) ${error.message}"
        is AuthError.ClientError -> "Logout failed: request error (${error.code}) ${error.message}"
        is AuthError.InvalidCredentials -> "Logout failed: invalid credentials (${error.message})"
        is AuthError.StorageError -> "Logout failed: storage error (${error.message})"
        is AuthError.Unknown -> "Logout failed: unknown error (${error.message})"
    }
}
