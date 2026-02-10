package com.connor.kwitter.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed interface HomeIntent

sealed interface HomeAction : HomeIntent {
    data object LogoutClick : HomeAction
    data object ErrorDismissed : HomeAction
    data class ToggleLike(
        val postId: String,
        val isCurrentlyLiked: Boolean,
        val currentLikeCount: Int
    ) : HomeAction

    data class ToggleBookmark(
        val postId: String,
        val isCurrentlyBookmarked: Boolean
    ) : HomeAction
}

sealed interface HomeNavAction : HomeIntent {
    data class PostClick(val postId: String) : HomeNavAction
    data object CreatePostClick : HomeNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : HomeNavAction
    data class AuthorClick(val userId: String) : HomeNavAction
}

class HomeViewModel(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<HomeAction>(Channel.UNLIMITED)

    val pagingFlow: Flow<PagingData<Post>> = postRepository
        .timelinePaging
        .cachedIn(viewModelScope)

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
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
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
                    is HomeAction.ToggleLike -> {
                        val newLiked = !action.isCurrentlyLiked
                        val newCount = if (action.isCurrentlyLiked) {
                            action.currentLikeCount - 1
                        } else {
                            action.currentLikeCount + 1
                        }
                        postRepository.updateLocalLikeState(action.postId, newLiked, newCount)

                        val result = if (action.isCurrentlyLiked) {
                            postRepository.unlikePost(action.postId)
                        } else {
                            postRepository.likePost(action.postId)
                        }
                        result.fold(
                            ifLeft = { error ->
                                postRepository.updateLocalLikeState(
                                    action.postId,
                                    action.isCurrentlyLiked,
                                    action.currentLikeCount
                                )
                                state.copy(error = formatError(error))
                            },
                            ifRight = { updatedStats ->
                                postRepository.updateLocalLikeState(
                                    action.postId,
                                    newLiked,
                                    updatedStats.likeCount
                                )
                                state
                            }
                        )
                    }
                    is HomeAction.ToggleBookmark -> {
                        val newBookmarked = !action.isCurrentlyBookmarked
                        postRepository.updateLocalBookmarkState(action.postId, newBookmarked)

                        val result = if (action.isCurrentlyBookmarked) {
                            postRepository.unbookmarkPost(action.postId)
                        } else {
                            postRepository.bookmarkPost(action.postId)
                        }
                        result.fold(
                            ifLeft = { error ->
                                postRepository.updateLocalBookmarkState(
                                    action.postId,
                                    action.isCurrentlyBookmarked
                                )
                                state.copy(error = formatError(error))
                            },
                            ifRight = { state }
                        )
                    }
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
