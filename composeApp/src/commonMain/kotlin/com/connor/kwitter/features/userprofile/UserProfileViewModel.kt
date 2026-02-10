package com.connor.kwitter.features.userprofile

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
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow

enum class ProfileTab { POSTS, REPLIES, LIKES }

data class UserProfileUiState(
    val profile: UserProfile? = null,
    val currentUserId: String? = null,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val posts: List<Post> = emptyList(),
    val postsHasMore: Boolean = false,
    val replies: List<Post> = emptyList(),
    val repliesHasMore: Boolean = false,
    val likes: List<Post> = emptyList(),
    val likesHasMore: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val isLoadingTab: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isFollowLoading: Boolean = false,
    val error: String? = null
) {
    val isOwnProfile: Boolean get() = currentUserId != null && currentUserId == profile?.id
}

sealed interface UserProfileIntent

sealed interface UserProfileAction : UserProfileIntent {
    data class Load(val userId: String) : UserProfileAction
    data class SelectTab(val tab: ProfileTab) : UserProfileAction
    data object LoadMore : UserProfileAction
    data object ToggleFollow : UserProfileAction
    data class ToggleLike(
        val postId: String,
        val isCurrentlyLiked: Boolean,
        val currentLikeCount: Int
    ) : UserProfileAction
    data class ToggleBookmark(
        val postId: String,
        val isCurrentlyBookmarked: Boolean
    ) : UserProfileAction
    data object ErrorDismissed : UserProfileAction
}

sealed interface UserProfileNavAction : UserProfileIntent {
    data object BackClick : UserProfileNavAction
    data class PostClick(val postId: String) : UserProfileNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : UserProfileNavAction
    data class AuthorClick(val userId: String) : UserProfileNavAction
}

class UserProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _events = Channel<UserProfileAction>(Channel.UNLIMITED)

    val uiState: StateFlow<UserProfileUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        UserProfilePresenter()
    }

    fun onEvent(event: UserProfileAction) {
        _events.trySend(event)
    }

    @Composable
    private fun UserProfilePresenter(): UserProfileUiState {
        var state by remember { mutableStateOf(UserProfileUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is UserProfileAction.Load -> loadProfile(action.userId, state)
                    is UserProfileAction.SelectTab -> selectTab(action.tab, state)
                    is UserProfileAction.LoadMore -> loadMore(state)
                    is UserProfileAction.ToggleFollow -> toggleFollow(state)
                    is UserProfileAction.ToggleLike -> handleToggleLike(action, state)
                    is UserProfileAction.ToggleBookmark -> handleToggleBookmark(action, state)
                    is UserProfileAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun loadProfile(
        userId: String,
        previousState: UserProfileUiState
    ): UserProfileUiState {
        val currentUserId = authRepository.currentUserId.first()
        val loadingState = previousState.copy(
            isLoadingProfile = true,
            error = null,
            currentUserId = currentUserId
        )

        val profileResult = userRepository.getUserProfile(userId)
        return profileResult.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingProfile = false,
                    error = formatError(error)
                )
            },
            ifRight = { profile ->
                val stateWithProfile = loadingState.copy(
                    isLoadingProfile = false,
                    profile = profile,
                    selectedTab = ProfileTab.POSTS
                )
                loadTabContent(ProfileTab.POSTS, userId, stateWithProfile)
            }
        )
    }

    private suspend fun selectTab(
        tab: ProfileTab,
        currentState: UserProfileUiState
    ): UserProfileUiState {
        val userId = currentState.profile?.id ?: return currentState
        if (tab == currentState.selectedTab) return currentState

        val newState = currentState.copy(selectedTab = tab)

        // Check if tab already has content loaded
        val hasContent = when (tab) {
            ProfileTab.POSTS -> newState.posts.isNotEmpty()
            ProfileTab.REPLIES -> newState.replies.isNotEmpty()
            ProfileTab.LIKES -> newState.likes.isNotEmpty()
        }
        if (hasContent) return newState

        return loadTabContent(tab, userId, newState)
    }

    private suspend fun loadTabContent(
        tab: ProfileTab,
        userId: String,
        currentState: UserProfileUiState
    ): UserProfileUiState {
        val loadingState = currentState.copy(isLoadingTab = true)
        val query = PostPageQuery(limit = PAGE_SIZE, offset = 0)

        val result = when (tab) {
            ProfileTab.POSTS -> userRepository.getUserPosts(userId, query)
            ProfileTab.REPLIES -> userRepository.getUserReplies(userId, query)
            ProfileTab.LIKES -> userRepository.getUserLikes(userId, query)
        }

        return result.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingTab = false,
                    error = formatError(error)
                )
            },
            ifRight = { postList ->
                val updated = when (tab) {
                    ProfileTab.POSTS -> loadingState.copy(
                        posts = postList.posts,
                        postsHasMore = postList.hasMore
                    )
                    ProfileTab.REPLIES -> loadingState.copy(
                        replies = postList.posts,
                        repliesHasMore = postList.hasMore
                    )
                    ProfileTab.LIKES -> loadingState.copy(
                        likes = postList.posts,
                        likesHasMore = postList.hasMore
                    )
                }
                updated.copy(isLoadingTab = false)
            }
        )
    }

    private suspend fun loadMore(currentState: UserProfileUiState): UserProfileUiState {
        val userId = currentState.profile?.id ?: return currentState
        if (currentState.isLoadingMore) return currentState

        val (currentList, hasMore) = when (currentState.selectedTab) {
            ProfileTab.POSTS -> currentState.posts to currentState.postsHasMore
            ProfileTab.REPLIES -> currentState.replies to currentState.repliesHasMore
            ProfileTab.LIKES -> currentState.likes to currentState.likesHasMore
        }

        if (!hasMore) return currentState

        val loadingState = currentState.copy(isLoadingMore = true)
        val query = PostPageQuery(limit = PAGE_SIZE, offset = currentList.size)

        val result = when (currentState.selectedTab) {
            ProfileTab.POSTS -> userRepository.getUserPosts(userId, query)
            ProfileTab.REPLIES -> userRepository.getUserReplies(userId, query)
            ProfileTab.LIKES -> userRepository.getUserLikes(userId, query)
        }

        return result.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoadingMore = false,
                    error = formatError(error)
                )
            },
            ifRight = { postList ->
                val updated = when (currentState.selectedTab) {
                    ProfileTab.POSTS -> loadingState.copy(
                        posts = currentList + postList.posts,
                        postsHasMore = postList.hasMore
                    )
                    ProfileTab.REPLIES -> loadingState.copy(
                        replies = currentList + postList.posts,
                        repliesHasMore = postList.hasMore
                    )
                    ProfileTab.LIKES -> loadingState.copy(
                        likes = currentList + postList.posts,
                        likesHasMore = postList.hasMore
                    )
                }
                updated.copy(isLoadingMore = false)
            }
        )
    }

    private suspend fun toggleFollow(currentState: UserProfileUiState): UserProfileUiState {
        val profile = currentState.profile ?: return currentState
        if (currentState.isOwnProfile) return currentState
        if (currentState.isFollowLoading) return currentState

        val isCurrentlyFollowing = profile.isFollowedByCurrentUser == true

        // Optimistic update
        val optimisticState = currentState.copy(
            profile = profile.copy(
                isFollowedByCurrentUser = !isCurrentlyFollowing,
                stats = profile.stats.copy(
                    followersCount = if (isCurrentlyFollowing) {
                        profile.stats.followersCount - 1
                    } else {
                        profile.stats.followersCount + 1
                    }
                )
            ),
            isFollowLoading = true
        )

        val result = if (isCurrentlyFollowing) {
            userRepository.unfollowUser(profile.id)
        } else {
            userRepository.followUser(profile.id)
        }

        return result.fold(
            ifLeft = { error ->
                // Revert optimistic update
                optimisticState.copy(
                    profile = profile,
                    isFollowLoading = false,
                    error = formatError(error)
                )
            },
            ifRight = {
                optimisticState.copy(isFollowLoading = false)
            }
        )
    }

    private suspend fun handleToggleLike(
        action: UserProfileAction.ToggleLike,
        currentState: UserProfileUiState
    ): UserProfileUiState {
        val newLiked = !action.isCurrentlyLiked
        val newCount = if (action.isCurrentlyLiked) {
            action.currentLikeCount - 1
        } else {
            action.currentLikeCount + 1
        }

        // Optimistic update in current tab
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
                // Revert
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
        action: UserProfileAction.ToggleBookmark,
        currentState: UserProfileUiState
    ): UserProfileUiState {
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

    private fun updatePostInState(
        state: UserProfileUiState,
        postId: String,
        transform: Post.() -> Post
    ): UserProfileUiState {
        return state.copy(
            posts = state.posts.map { if (it.id == postId) it.transform() else it },
            replies = state.replies.map { if (it.id == postId) it.transform() else it },
            likes = state.likes.map { if (it.id == postId) it.transform() else it }
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

    private fun formatPostError(error: com.connor.kwitter.domain.post.model.PostError): String = when (error) {
        is com.connor.kwitter.domain.post.model.PostError.NetworkError -> "Network error: ${error.message}"
        is com.connor.kwitter.domain.post.model.PostError.ServerError -> "Server error (${error.code}): ${error.message}"
        is com.connor.kwitter.domain.post.model.PostError.ClientError -> "Request error (${error.code}): ${error.message}"
        is com.connor.kwitter.domain.post.model.PostError.Unauthorized -> "Authentication required"
        is com.connor.kwitter.domain.post.model.PostError.NotFound -> "Not found"
        is com.connor.kwitter.domain.post.model.PostError.Unknown -> "Unknown error: ${error.message}"
    }
}
