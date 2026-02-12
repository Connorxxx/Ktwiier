package com.connor.kwitter.features.search

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
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.search.model.SearchError
import com.connor.kwitter.domain.search.repository.SearchRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserListItem
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class SearchTab { POSTS, REPLIES, USERS }

data class SearchUiState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.POSTS,
    val sortOrder: String = "best_match",
    val posts: List<Post> = emptyList(),
    val postsHasMore: Boolean = false,
    val replies: List<Post> = emptyList(),
    val repliesHasMore: Boolean = false,
    val users: List<UserListItem> = emptyList(),
    val usersHasMore: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingTab: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

sealed interface SearchIntent

sealed interface SearchAction : SearchIntent {
    data class Search(val query: String) : SearchAction
    data class SelectTab(val tab: SearchTab) : SearchAction
    data object LoadMore : SearchAction
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

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _events = Channel<SearchAction>(Channel.UNLIMITED)

    val uiState: StateFlow<SearchUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        SearchPresenter()
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
                    is SearchAction.Search -> performSearch(action.query, state)
                    is SearchAction.SelectTab -> selectTab(action.tab, state)
                    is SearchAction.LoadMore -> loadMore(state)
                    is SearchAction.SetSortOrder -> setSortOrder(action.sort, state)
                    is SearchAction.ToggleLike -> handleToggleLike(action, state)
                    is SearchAction.ToggleBookmark -> handleToggleBookmark(action, state)
                    is SearchAction.ToggleFollow -> handleToggleFollow(action, state)
                    is SearchAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun performSearch(
        query: String,
        currentState: SearchUiState
    ): SearchUiState {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return currentState.copy(query = trimmed)

        val searchingState = currentState.copy(
            query = trimmed,
            isSearching = true,
            hasSearched = true,
            selectedTab = SearchTab.POSTS,
            posts = emptyList(),
            postsHasMore = false,
            replies = emptyList(),
            repliesHasMore = false,
            users = emptyList(),
            usersHasMore = false,
            error = null
        )

        val result = searchRepository.searchPosts(
            query = trimmed,
            sort = searchingState.sortOrder,
            limit = PAGE_SIZE,
            offset = 0
        )

        return result.fold(
            ifLeft = { error ->
                searchingState.copy(
                    isSearching = false,
                    error = formatSearchError(error)
                )
            },
            ifRight = { postList ->
                searchingState.copy(
                    isSearching = false,
                    posts = postList.posts,
                    postsHasMore = postList.hasMore
                )
            }
        )
    }

    private suspend fun selectTab(
        tab: SearchTab,
        currentState: SearchUiState
    ): SearchUiState {
        if (tab == currentState.selectedTab) return currentState
        if (currentState.query.isBlank()) return currentState.copy(selectedTab = tab)

        val newState = currentState.copy(selectedTab = tab)

        val hasContent = when (tab) {
            SearchTab.POSTS -> newState.posts.isNotEmpty()
            SearchTab.REPLIES -> newState.replies.isNotEmpty()
            SearchTab.USERS -> newState.users.isNotEmpty()
        }
        if (hasContent) return newState

        return loadTabContent(tab, newState)
    }

    private suspend fun loadTabContent(
        tab: SearchTab,
        currentState: SearchUiState
    ): SearchUiState {
        val loadingState = currentState.copy(isLoadingTab = true)

        return when (tab) {
            SearchTab.POSTS -> {
                val result = searchRepository.searchPosts(
                    query = currentState.query,
                    sort = currentState.sortOrder,
                    limit = PAGE_SIZE,
                    offset = 0
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingTab = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { postList ->
                        loadingState.copy(
                            isLoadingTab = false,
                            posts = postList.posts,
                            postsHasMore = postList.hasMore
                        )
                    }
                )
            }
            SearchTab.REPLIES -> {
                val result = searchRepository.searchReplies(
                    query = currentState.query,
                    sort = currentState.sortOrder,
                    limit = PAGE_SIZE,
                    offset = 0
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingTab = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { postList ->
                        loadingState.copy(
                            isLoadingTab = false,
                            replies = postList.posts,
                            repliesHasMore = postList.hasMore
                        )
                    }
                )
            }
            SearchTab.USERS -> {
                val result = searchRepository.searchUsers(
                    query = currentState.query,
                    limit = PAGE_SIZE,
                    offset = 0
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingTab = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { userList ->
                        loadingState.copy(
                            isLoadingTab = false,
                            users = userList.users,
                            usersHasMore = userList.hasMore
                        )
                    }
                )
            }
        }
    }

    private suspend fun loadMore(currentState: SearchUiState): SearchUiState {
        if (currentState.isLoadingMore || currentState.query.isBlank()) return currentState

        return when (currentState.selectedTab) {
            SearchTab.POSTS -> {
                if (!currentState.postsHasMore) return currentState
                val loadingState = currentState.copy(isLoadingMore = true)
                val result = searchRepository.searchPosts(
                    query = currentState.query,
                    sort = currentState.sortOrder,
                    limit = PAGE_SIZE,
                    offset = currentState.posts.size
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingMore = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { postList ->
                        loadingState.copy(
                            isLoadingMore = false,
                            posts = currentState.posts + postList.posts,
                            postsHasMore = postList.hasMore
                        )
                    }
                )
            }
            SearchTab.REPLIES -> {
                if (!currentState.repliesHasMore) return currentState
                val loadingState = currentState.copy(isLoadingMore = true)
                val result = searchRepository.searchReplies(
                    query = currentState.query,
                    sort = currentState.sortOrder,
                    limit = PAGE_SIZE,
                    offset = currentState.replies.size
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingMore = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { postList ->
                        loadingState.copy(
                            isLoadingMore = false,
                            replies = currentState.replies + postList.posts,
                            repliesHasMore = postList.hasMore
                        )
                    }
                )
            }
            SearchTab.USERS -> {
                if (!currentState.usersHasMore) return currentState
                val loadingState = currentState.copy(isLoadingMore = true)
                val result = searchRepository.searchUsers(
                    query = currentState.query,
                    limit = PAGE_SIZE,
                    offset = currentState.users.size
                )
                result.fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoadingMore = false,
                            error = formatSearchError(error)
                        )
                    },
                    ifRight = { userList ->
                        loadingState.copy(
                            isLoadingMore = false,
                            users = currentState.users + userList.users,
                            usersHasMore = userList.hasMore
                        )
                    }
                )
            }
        }
    }

    private suspend fun setSortOrder(
        sort: String,
        currentState: SearchUiState
    ): SearchUiState {
        if (sort == currentState.sortOrder) return currentState
        val newState = currentState.copy(sortOrder = sort)
        if (newState.query.isBlank() || !newState.hasSearched) return newState

        // Re-search with new sort order, clearing existing results for current tab
        val clearedState = when (newState.selectedTab) {
            SearchTab.POSTS -> newState.copy(posts = emptyList(), postsHasMore = false)
            SearchTab.REPLIES -> newState.copy(replies = emptyList(), repliesHasMore = false)
            SearchTab.USERS -> newState // Users don't have sort
        }
        return loadTabContent(clearedState.selectedTab, clearedState)
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

        val optimisticState = updatePostInState(currentState, action.postId) {
            copy(
                isLikedByCurrentUser = newLiked,
                stats = stats.copy(likeCount = newCount)
            )
        }

        val result = if (action.isCurrentlyLiked) {
            postRepository.unlikePost(action.postId)
        } else {
            postRepository.likePost(action.postId)
        }

        return result.fold(
            ifLeft = { error ->
                updatePostInState(optimisticState, action.postId) {
                    copy(
                        isLikedByCurrentUser = action.isCurrentlyLiked,
                        stats = stats.copy(likeCount = action.currentLikeCount)
                    )
                }.copy(error = formatPostError(error))
            },
            ifRight = { updatedStats ->
                updatePostInState(optimisticState, action.postId) {
                    copy(stats = updatedStats)
                }
            }
        )
    }

    private suspend fun handleToggleBookmark(
        action: SearchAction.ToggleBookmark,
        currentState: SearchUiState
    ): SearchUiState {
        val newBookmarked = !action.isCurrentlyBookmarked

        val optimisticState = updatePostInState(currentState, action.postId) {
            copy(isBookmarkedByCurrentUser = newBookmarked)
        }

        val result = if (action.isCurrentlyBookmarked) {
            postRepository.unbookmarkPost(action.postId)
        } else {
            postRepository.bookmarkPost(action.postId)
        }

        return result.fold(
            ifLeft = { error ->
                updatePostInState(optimisticState, action.postId) {
                    copy(isBookmarkedByCurrentUser = action.isCurrentlyBookmarked)
                }.copy(error = formatPostError(error))
            },
            ifRight = { optimisticState }
        )
    }

    private suspend fun handleToggleFollow(
        action: SearchAction.ToggleFollow,
        currentState: SearchUiState
    ): SearchUiState {
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
                optimisticState.copy(
                    users = optimisticState.users.map { user ->
                        if (user.id == action.targetUserId) {
                            user.copy(isFollowedByCurrentUser = action.isCurrentlyFollowing)
                        } else user
                    },
                    error = formatUserError(error)
                )
            },
            ifRight = { optimisticState }
        )
    }

    private fun updatePostInState(
        state: SearchUiState,
        postId: String,
        transform: Post.() -> Post
    ): SearchUiState {
        return state.copy(
            posts = state.posts.map { if (it.id == postId) it.transform() else it },
            replies = state.replies.map { if (it.id == postId) it.transform() else it }
        )
    }

    private fun formatSearchError(error: SearchError): String = when (error) {
        is SearchError.NetworkError -> "Network error: ${error.message}"
        is SearchError.ServerError -> "Server error (${error.code}): ${error.message}"
        is SearchError.ClientError -> "Request error (${error.code}): ${error.message}"
        is SearchError.Unauthorized -> "Authentication required"
        is SearchError.NotFound -> "Not found"
        is SearchError.Unknown -> "Unknown error: ${error.message}"
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
