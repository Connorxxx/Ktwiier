package com.connor.kwitter.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.search.repository.SearchRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab { POSTS, REPLIES, USERS }

data class SearchUiState(
    val selectedTab: SearchTab = SearchTab.POSTS,
    val sortOrder: String = "best_match",
    val hasSearched: Boolean = false,
    val error: String? = null
)

sealed interface SearchIntent

sealed interface SearchAction : SearchIntent {
    data class Search(val query: String) : SearchAction
    data class SelectTab(val tab: SearchTab) : SearchAction
    data class SetSortOrder(val sort: String) : SearchAction
    data class ToggleLike(
        val postId: String,
        val isCurrentlyLiked: Boolean,
        val currentLikeCount: Int
    ) : SearchAction

    data class ToggleBookmark(
        val postId: String,
        val isCurrentlyBookmarked: Boolean
    ) : SearchAction

    data class ToggleFollow(
        val targetUserId: String,
        val isCurrentlyFollowing: Boolean
    ) : SearchAction

    data object ErrorDismissed : SearchAction
}

sealed interface SearchNavAction : SearchIntent {
    data object BackClick : SearchNavAction
    data class PostClick(val postId: String) : SearchNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : SearchNavAction
    data class AuthorClick(val userId: String) : SearchNavAction
    data class UserClick(val userId: String) : SearchNavAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private data class SearchQuery(val query: String = "", val sort: String = "best_match")

    private data class PostModification(
        val isLikedByCurrentUser: Boolean? = null,
        val likeCount: Int? = null,
        val isBookmarkedByCurrentUser: Boolean? = null
    )

    private val _searchQuery = MutableStateFlow(SearchQuery())
    private val _uiState = MutableStateFlow(SearchUiState())
    private val _postMods = MutableStateFlow<Map<String, PostModification>>(emptyMap())
    private val _userMods = MutableStateFlow<Map<String, Boolean?>>(emptyMap())

    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

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
        when (event) {
            is SearchAction.Search -> search(event.query)
            is SearchAction.SelectTab -> _uiState.update { it.copy(selectedTab = event.tab) }
            is SearchAction.SetSortOrder -> setSortOrder(event.sort)
            is SearchAction.ToggleLike -> viewModelScope.launch { handleToggleLike(event) }
            is SearchAction.ToggleBookmark -> viewModelScope.launch { handleToggleBookmark(event) }
            is SearchAction.ToggleFollow -> viewModelScope.launch { handleToggleFollow(event) }
            is SearchAction.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _postMods.value = emptyMap()
        _userMods.value = emptyMap()
        _searchQuery.value = SearchQuery(trimmed, _uiState.value.sortOrder)
        _uiState.update {
            it.copy(selectedTab = SearchTab.POSTS, hasSearched = true, error = null)
        }
    }

    private fun setSortOrder(sort: String) {
        if (sort == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = sort) }
        val current = _searchQuery.value
        if (current.query.isNotBlank()) {
            _searchQuery.value = current.copy(sort = sort)
        }
    }

    private suspend fun handleToggleLike(action: SearchAction.ToggleLike) {
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

        val result = if (action.isCurrentlyLiked) {
            postRepository.unlikePost(action.postId)
        } else {
            postRepository.likePost(action.postId)
        }

        result.fold(
            ifLeft = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isLikedByCurrentUser = action.isCurrentlyLiked,
                        likeCount = action.currentLikeCount
                    ))
                }
                _uiState.update { it.copy(error = formatPostError(error)) }
            },
            ifRight = { updatedStats ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isLikedByCurrentUser = newLiked,
                        likeCount = updatedStats.likeCount
                    ))
                }
            }
        )
    }

    private suspend fun handleToggleBookmark(action: SearchAction.ToggleBookmark) {
        val newBookmarked = !action.isCurrentlyBookmarked

        _postMods.update { mods ->
            val existing = mods[action.postId] ?: PostModification()
            mods + (action.postId to existing.copy(isBookmarkedByCurrentUser = newBookmarked))
        }

        val result = if (action.isCurrentlyBookmarked) {
            postRepository.unbookmarkPost(action.postId)
        } else {
            postRepository.bookmarkPost(action.postId)
        }

        result.fold(
            ifLeft = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isBookmarkedByCurrentUser = action.isCurrentlyBookmarked
                    ))
                }
                _uiState.update { it.copy(error = formatPostError(error)) }
            },
            ifRight = { /* keep optimistic state */ }
        )
    }

    private suspend fun handleToggleFollow(action: SearchAction.ToggleFollow) {
        val newFollowed = !action.isCurrentlyFollowing

        _userMods.update { it + (action.targetUserId to newFollowed) }

        val result = if (action.isCurrentlyFollowing) {
            userRepository.unfollowUser(action.targetUserId)
        } else {
            userRepository.followUser(action.targetUserId)
        }

        result.fold(
            ifLeft = { error ->
                _userMods.update { it + (action.targetUserId to action.isCurrentlyFollowing) }
                _uiState.update { it.copy(error = formatUserError(error)) }
            },
            ifRight = { /* keep optimistic state */ }
        )
    }

    private fun Post.applyMods(mods: Map<String, PostModification>): Post {
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
