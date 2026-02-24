package com.connor.kwitter.features.userprofile

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
import com.connor.kwitter.core.result.Result
import com.connor.kwitter.core.result.uiResultOf
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

enum class ProfileTab { POSTS, REPLIES, LIKES }

data class UserProfileUiState(
    val profile: com.connor.kwitter.domain.user.model.UserProfile? = null,
    val currentUserId: String? = null,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val isLoadingProfile: Boolean = false,
    val isFollowLoading: Boolean = false,
    val error: String? = null
) {
    val isOwnProfile: Boolean get() = currentUserId != null && currentUserId == profile?.id
    val operationResult: Result<Unit, String>
        get() = uiResultOf(
            isLoading = isLoadingProfile || isFollowLoading,
            error = error
        )
}

sealed interface UserProfileIntent

sealed interface UserProfileAction : UserProfileIntent {
    data class Load(val userId: String) : UserProfileAction
    data object Refresh : UserProfileAction
    data class SelectTab(val tab: ProfileTab) : UserProfileAction
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
    data object EditProfileClick : UserProfileNavAction
    data object FollowingClick : UserProfileNavAction
    data object FollowersClick : UserProfileNavAction
    data class MessageClick(val userId: String, val displayName: String) : UserProfileNavAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private data class PostModification(
        val isLikedByCurrentUser: Boolean? = null,
        val likeCount: Int? = null,
        val isBookmarkedByCurrentUser: Boolean? = null
    )

    private val _events = Channel<UserProfileAction>(Channel.UNLIMITED)
    private val _userId = MutableStateFlow("")
    private val _refreshTrigger = MutableStateFlow(0)
    private val _postMods = MutableStateFlow<Map<String, PostModification>>(emptyMap())

    val uiState: StateFlow<UserProfileUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        UserProfilePresenter()
    }

    private val pagingTrigger = combine(_userId, _refreshTrigger) { userId, _ -> userId }

    val postsPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId.isBlank()) flowOf(PagingData.empty())
            else userRepository.userPostsPaging(userId)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val repliesPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId.isBlank()) flowOf(PagingData.empty())
            else userRepository.userRepliesPaging(userId)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val likesPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId.isBlank()) flowOf(PagingData.empty())
            else userRepository.userLikesPaging(userId)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
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
                    is UserProfileAction.Refresh -> {
                        val userId = state.profile?.id
                        if (userId != null) {
                            _postMods.value = emptyMap()
                            _refreshTrigger.value++
                            loadProfile(userId, state)
                        } else state
                    }
                    is UserProfileAction.SelectTab -> state.copy(selectedTab = action.tab)
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
        _userId.value = userId
        _postMods.value = emptyMap()
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
                loadingState.copy(
                    isLoadingProfile = false,
                    profile = profile,
                    selectedTab = ProfileTab.POSTS
                )
            }
        )
    }

    private suspend fun toggleFollow(currentState: UserProfileUiState): UserProfileUiState {
        val profile = currentState.profile ?: return currentState
        if (currentState.isOwnProfile) return currentState
        if (currentState.isFollowLoading) return currentState

        val isCurrentlyFollowing = profile.isFollowedByCurrentUser == true

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

        return result.fold(
            ifLeft = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isLikedByCurrentUser = action.isCurrentlyLiked,
                        likeCount = action.currentLikeCount
                    ))
                }
                currentState.copy(error = formatPostError(error))
            },
            ifRight = { updatedStats ->
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
        action: UserProfileAction.ToggleBookmark,
        currentState: UserProfileUiState
    ): UserProfileUiState {
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

        return result.fold(
            ifLeft = { error ->
                _postMods.update { mods ->
                    val existing = mods[action.postId] ?: PostModification()
                    mods + (action.postId to existing.copy(
                        isBookmarkedByCurrentUser = action.isCurrentlyBookmarked
                    ))
                }
                currentState.copy(error = formatPostError(error))
            },
            ifRight = { currentState }
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
