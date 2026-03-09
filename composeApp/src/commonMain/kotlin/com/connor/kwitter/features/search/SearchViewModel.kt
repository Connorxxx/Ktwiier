package com.connor.kwitter.features.search

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
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.search.repository.SearchRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

enum class SearchTab { POSTS, REPLIES, USERS }

data class SearchUiState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.POSTS,
    val sortOrder: String = "relevance",
    val hasSearched: Boolean = false,
    val error: String? = null
) {
    val operationResult: Result<Unit, String>
        get() = uiResultOf(isLoading = false, error = error)
}

sealed interface SearchIntent

sealed interface SearchAction : SearchIntent {
    data class UpdateQuery(val query: String) : SearchAction
    data object SubmitSearch : SearchAction
    data class SelectTab(val tab: SearchTab) : SearchAction
    data class SetSortOrder(val sort: String) : SearchAction
    data class ToggleLike(
        val postId: Long,
        val isCurrentlyLiked: Boolean,
        val currentLikeCount: Int
    ) : SearchAction

    data class ToggleBookmark(
        val postId: Long,
        val isCurrentlyBookmarked: Boolean
    ) : SearchAction

    data class ToggleFollow(
        val targetUserId: Long,
        val isCurrentlyFollowing: Boolean
    ) : SearchAction

    data object ErrorDismissed : SearchAction
}

sealed interface SearchNavAction : SearchIntent {
    data object BackClick : SearchNavAction
    data class PostClick(val postId: Long) : SearchNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : SearchNavAction
    data class AuthorClick(val userId: Long) : SearchNavAction
    data class UserClick(val userId: Long) : SearchNavAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private data class SearchQuery(val query: String = "", val sort: String = "relevance")

    private data class PostModification(
        val isLikedByCurrentUser: Boolean? = null,
        val likeCount: Int? = null,
        val isBookmarkedByCurrentUser: Boolean? = null
    )

    private val _events = Channel<SearchAction>(Channel.UNLIMITED)
    private val _searchQuery = MutableStateFlow(SearchQuery())
    private val _postMods = MutableStateFlow<Map<Long, PostModification>>(emptyMap())
    private val _userMods = MutableStateFlow<Map<Long, Boolean?>>(emptyMap())

    val uiState: StateFlow<SearchUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        SearchPresenter()
    }

    private val postQueryFlow = _searchQuery
        .map { it.query to it.sort }
        .distinctUntilChanged()

    val postsPaging: Flow<PagingData<Post>> = postQueryFlow
        .flatMapLatest { (query, sort) ->
            if (query.isBlank()) flowOf(PagingData.empty())
            else searchRepository.searchPostsPaging(query, sort)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val repliesPaging: Flow<PagingData<Post>> = postQueryFlow
        .flatMapLatest { (query, sort) ->
            if (query.isBlank()) flowOf(PagingData.empty())
            else searchRepository.searchRepliesPaging(query, sort)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val usersPaging: Flow<PagingData<UserListItem>> = _searchQuery
        .map { it.query }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(PagingData.empty())
            else searchRepository.searchUsersPaging(query)
        }
        .cachedIn(viewModelScope)
        .combine(_userMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { user ->
                mods[user.id]?.let { user.copy(isFollowedByCurrentUser = it) } ?: user
            }
        }

    fun onEvent(event: SearchAction) {
        _events.trySend(event)
    }

    @Composable
    private fun SearchPresenter(): SearchUiState {
        var state by remember { mutableStateOf(SearchUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is SearchAction.UpdateQuery -> state.copy(query = action.query)
                    SearchAction.SubmitSearch -> handleSearch(state)
                    is SearchAction.SelectTab -> state.copy(selectedTab = action.tab)
                    is SearchAction.SetSortOrder -> handleSetSortOrder(action.sort, state)
                    is SearchAction.ToggleLike -> handleToggleLike(action, state)
                    is SearchAction.ToggleBookmark -> handleToggleBookmark(action, state)
                    is SearchAction.ToggleFollow -> handleToggleFollow(action, state)
                    is SearchAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun handleSearch(currentState: SearchUiState): SearchUiState {
        val trimmed = currentState.query.trim()
        if (trimmed.isBlank()) return currentState
        _postMods.value = emptyMap()
        _userMods.value = emptyMap()
        _searchQuery.value = SearchQuery(trimmed, currentState.sortOrder)
        return currentState.copy(selectedTab = SearchTab.POSTS, hasSearched = true, error = null)
    }

    private fun handleSetSortOrder(sort: String, currentState: SearchUiState): SearchUiState {
        if (sort == currentState.sortOrder) return currentState
        val current = _searchQuery.value
        if (current.query.isNotBlank()) {
            _searchQuery.value = current.copy(sort = sort)
        }
        return currentState.copy(sortOrder = sort)
    }

    private suspend fun handleToggleLike(
        action: SearchAction.ToggleLike,
        currentState: SearchUiState
    ): SearchUiState {
        val newLiked = !action.isCurrentlyLiked
        val newCount = if (action.isCurrentlyLiked) {
            action.currentLikeCount - 1
        } else {
            action.currentLikeCount + 1
        }

        _postMods.update { mods ->
            val existing = mods[action.postId] ?: PostModification()
            mods + (action.postId to existing.copy(
                isLikedByCurrentUser = newLiked,
                likeCount = newCount
            ))
        }

        return fold(
            block = {
                if (action.isCurrentlyLiked) {
                    postRepository.unlikePost(action.postId)
                } else {
                    postRepository.likePost(action.postId)
                }
            },
            recover = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isLikedByCurrentUser = action.isCurrentlyLiked,
                        likeCount = action.currentLikeCount
                    ))
                }
                currentState.copy(error = formatPostError(error))
            },
            transform = { updatedStats ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isLikedByCurrentUser = newLiked,
                        likeCount = updatedStats.likeCount
                    ))
                }
                currentState
            }
        )
    }

    private suspend fun handleToggleBookmark(
        action: SearchAction.ToggleBookmark,
        currentState: SearchUiState
    ): SearchUiState {
        val newBookmarked = !action.isCurrentlyBookmarked

        _postMods.update { mods ->
            val existing = mods[action.postId] ?: PostModification()
            mods + (action.postId to existing.copy(isBookmarkedByCurrentUser = newBookmarked))
        }

        return fold(
            block = {
                if (action.isCurrentlyBookmarked) {
                    postRepository.unbookmarkPost(action.postId)
                } else {
                    postRepository.bookmarkPost(action.postId)
                }
            },
            recover = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isBookmarkedByCurrentUser = action.isCurrentlyBookmarked
                    ))
                }
                currentState.copy(error = formatPostError(error))
            },
            transform = { currentState /* keep optimistic state */ }
        )
    }

    private suspend fun handleToggleFollow(
        action: SearchAction.ToggleFollow,
        currentState: SearchUiState
    ): SearchUiState {
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
                currentState.copy(error = formatUserError(error))
            },
            transform = { currentState /* keep optimistic state */ }
        )
    }

    private fun Post.applyMods(mods: Map<Long, PostModification>): Post {
        val mod = mods[id] ?: return this
        return copy(
            isLikedByCurrentUser = mod.isLikedByCurrentUser ?: isLikedByCurrentUser,
            stats = if (mod.likeCount != null) stats.copy(likeCount = mod.likeCount) else stats,
            isBookmarkedByCurrentUser = mod.isBookmarkedByCurrentUser ?: isBookmarkedByCurrentUser
        )
    }

    private fun formatPostError(error: PostError): String = when (error) {
        is PostError.NetworkError -> "Network error: ${error.message}"
        is PostError.ServerError -> "Server error (${error.code}): ${error.message}"
        is PostError.ClientError -> "Request error (${error.code}): ${error.message}"
        is PostError.Unauthorized -> "Authentication required"
        is PostError.NotFound -> "Not found"
        is PostError.Unknown -> "Unknown error: ${error.message}"
    }

    private fun formatUserError(error: UserError): String = when (error) {
        is UserError.NetworkError -> "Network error: ${error.message}"
        is UserError.ServerError -> "Server error (${error.code}): ${error.message}"
        is UserError.ClientError -> "Request error (${error.code}): ${error.message}"
        is UserError.Unauthorized -> "Authentication required"
        is UserError.NotFound -> "User not found"
        is UserError.Unknown -> "Unknown error: ${error.message}"
    }
}
