package com.connor.kwitter.features.userlist

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
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class UserListType { FOLLOWING, FOLLOWERS }

data class UserListUiState(
    val listType: UserListType = UserListType.FOLLOWING,
    val userId: String = "",
    val displayName: String = "",
    val users: List<UserListItem> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

sealed interface UserListIntent

sealed interface UserListAction : UserListIntent {
    data class Load(
        val userId: String,
        val displayName: String,
        val listType: UserListType
    ) : UserListAction
    data object LoadMore : UserListAction
    data class ToggleFollow(
        val targetUserId: String,
        val isCurrentlyFollowing: Boolean
    ) : UserListAction
    data object ErrorDismissed : UserListAction
}

sealed interface UserListNavAction : UserListIntent {
    data object BackClick : UserListNavAction
    data class UserClick(val userId: String) : UserListNavAction
}

class UserListViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _events = Channel<UserListAction>(Channel.UNLIMITED)

    val uiState: StateFlow<UserListUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        UserListPresenter()
    }

    fun onEvent(event: UserListAction) {
        _events.trySend(event)
    }

    @Composable
    private fun UserListPresenter(): UserListUiState {
        var state by remember { mutableStateOf(UserListUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is UserListAction.Load -> loadList(action)
                    is UserListAction.LoadMore -> loadMore(state)
                    is UserListAction.ToggleFollow -> toggleFollow(action, state)
                    is UserListAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun loadList(action: UserListAction.Load): UserListUiState {
        val loadingState = UserListUiState(
            listType = action.listType,
            userId = action.userId,
            displayName = action.displayName,
            isLoading = true
        )

        val result = when (action.listType) {
            UserListType.FOLLOWING -> userRepository.getUserFollowing(
                action.userId, PAGE_SIZE, 0
            )
            UserListType.FOLLOWERS -> userRepository.getUserFollowers(
                action.userId, PAGE_SIZE, 0
            )
        }

        return result.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoading = false,
                    error = formatError(error)
                )
            },
            ifRight = { userList ->
                loadingState.copy(
                    isLoading = false,
                    users = userList.users,
                    hasMore = userList.hasMore
                )
            }
        )
    }

    private suspend fun loadMore(currentState: UserListUiState): UserListUiState {
        if (currentState.isLoadingMore || !currentState.hasMore) return currentState

        val loadingState = currentState.copy(isLoadingMore = true)
        val offset = currentState.users.size

        val result = when (currentState.listType) {
            UserListType.FOLLOWING -> userRepository.getUserFollowing(
                currentState.userId, PAGE_SIZE, offset
            )
            UserListType.FOLLOWERS -> userRepository.getUserFollowers(
                currentState.userId, PAGE_SIZE, offset
            )
        }

        return result.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingMore = false,
                    error = formatError(error)
                )
            },
            ifRight = { userList ->
                loadingState.copy(
                    isLoadingMore = false,
                    users = currentState.users + userList.users,
                    hasMore = userList.hasMore
                )
            }
        )
    }

    private suspend fun toggleFollow(
        action: UserListAction.ToggleFollow,
        currentState: UserListUiState
    ): UserListUiState {
        // Optimistic update
        val optimisticState = currentState.copy(
            users = currentState.users.map { user ->
                if (user.id == action.targetUserId) {
                    user.copy(isFollowedByCurrentUser = !action.isCurrentlyFollowing)
                } else user
            }
        )

        val result = if (action.isCurrentlyFollowing) {
            userRepository.unfollowUser(action.targetUserId)
        } else {
            userRepository.followUser(action.targetUserId)
        }

        return result.fold(
            ifLeft = { error ->
                // Revert optimistic update
                optimisticState.copy(
                    users = optimisticState.users.map { user ->
                        if (user.id == action.targetUserId) {
                            user.copy(isFollowedByCurrentUser = action.isCurrentlyFollowing)
                        } else user
                    },
                    error = formatError(error)
                )
            },
            ifRight = { optimisticState }
        )
    }

    private fun formatError(error: UserError): String = when (error) {
        is UserError.NetworkError -> "Network error: ${error.message}"
        is UserError.ServerError -> "Server error (${error.code}): ${error.message}"
        is UserError.ClientError -> "Request error (${error.code}): ${error.message}"
        is UserError.Unauthorized -> "Authentication required"
        is UserError.NotFound -> "User not found"
        is UserError.Unknown -> "Unknown error: ${error.message}"
    }
}
