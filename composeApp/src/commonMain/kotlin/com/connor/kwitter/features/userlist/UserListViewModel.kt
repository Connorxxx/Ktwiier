package com.connor.kwitter.features.userlist

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
import androidx.paging.map
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import arrow.core.raise.fold
import com.connor.kwitter.core.result.Result
import com.connor.kwitter.core.result.uiResultOf
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

enum class UserListType { FOLLOWING, FOLLOWERS }

data class UserListUiState(
    val listType: UserListType = UserListType.FOLLOWING,
    val userId: Long = 0L,
    val displayName: String = "",
    val error: String? = null
) {
    val operationResult: Result<Unit, String>
        get() = uiResultOf(isLoading = false, error = error)
}

sealed interface UserListIntent

sealed interface UserListAction : UserListIntent {
    data class Load(
        val userId: Long,
        val displayName: String,
        val listType: UserListType
    ) : UserListAction
    data class ToggleFollow(
        val targetUserId: Long,
        val isCurrentlyFollowing: Boolean
    ) : UserListAction
    data object ErrorDismissed : UserListAction
}

sealed interface UserListNavAction : UserListIntent {
    data object BackClick : UserListNavAction
    data class UserClick(val userId: Long) : UserListNavAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserListViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private data class UserListQuery(
        val userId: Long,
        val listType: UserListType
    )

    private val _events = Channel<UserListAction>(Channel.UNLIMITED)
    private val _query = MutableStateFlow<UserListQuery?>(null)
    private val _userMods = MutableStateFlow<Map<Long, Boolean?>>(emptyMap())

    val uiState: StateFlow<UserListUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        UserListPresenter()
    }

    val usersPaging: Flow<PagingData<UserListItem>> = _query
        .flatMapLatest { query ->
            if (query == null) flowOf(PagingData.empty())
            else when (query.listType) {
                UserListType.FOLLOWING -> userRepository.userFollowingPaging(query.userId)
                UserListType.FOLLOWERS -> userRepository.userFollowersPaging(query.userId)
            }
        }
        .cachedIn(viewModelScope)
        .combine(_userMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { user ->
                mods[user.id]?.let { user.copy(isFollowedByCurrentUser = it) } ?: user
            }
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
                    is UserListAction.Load -> {
                        _userMods.value = emptyMap()
                        _query.value = UserListQuery(action.userId, action.listType)
                        state.copy(
                            listType = action.listType,
                            userId = action.userId,
                            displayName = action.displayName,
                            error = null
                        )
                    }
                    is UserListAction.ToggleFollow -> toggleFollow(action, state)
                    is UserListAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun toggleFollow(
        action: UserListAction.ToggleFollow,
        currentState: UserListUiState
    ): UserListUiState {
        val newFollowed = !action.isCurrentlyFollowing

        _userMods.update { it + (action.targetUserId to newFollowed) }

        return fold(
            block = {
                if (action.isCurrentlyFollowing) {
                    userRepository.unfollowUser(action.targetUserId)
                } else {
                    userRepository.followUser(action.targetUserId)
                }
            },
            recover = { error ->
                _userMods.update { it + (action.targetUserId to action.isCurrentlyFollowing) }
                currentState.copy(error = formatError(error))
            },
            transform = { currentState }
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
