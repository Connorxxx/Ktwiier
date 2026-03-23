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
import arrow.core.raise.fold
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.repository.PostRepository
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.plus

enum class ProfileTab { POSTS, REPLIES, LIKES }

sealed interface UserProfileScreenState {
    data object Loading : UserProfileScreenState
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : UserProfileScreenState
    data class Content(
        val profile: UserProfile,
        val currentUserId: Long?,
        val isFollowLoading: Boolean = false,
        val bannerMessage: String? = null
    ) : UserProfileScreenState
}

data class UserProfileUiState(
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val screenState: UserProfileScreenState = UserProfileScreenState.Loading
) {
    val content: UserProfileScreenState.Content?
        get() = screenState as? UserProfileScreenState.Content

    val profile: UserProfile?
        get() = content?.profile

    val currentUserId: Long?
        get() = content?.currentUserId

    val isOwnProfile: Boolean
        get() = currentUserId != null && currentUserId == profile?.id

    val isFollowLoading: Boolean
        get() = content?.isFollowLoading == true

    val bannerMessage: String?
        get() = content?.bannerMessage
}

sealed interface UserProfileIntent

sealed interface UserProfileAction : UserProfileIntent {
    data class Load(val userId: Long) : UserProfileAction
    data object Refresh : UserProfileAction
    data class SelectTab(val tab: ProfileTab) : UserProfileAction
    data object ToggleFollow : UserProfileAction
    data class ToggleLike(
        val postId: Long,
        val isCurrentlyLiked: Boolean,
        val currentLikeCount: Int
    ) : UserProfileAction
    data class ToggleBookmark(
        val postId: Long,
        val isCurrentlyBookmarked: Boolean
    ) : UserProfileAction
    data object ErrorDismissed : UserProfileAction
}

sealed interface UserProfileNavAction : UserProfileIntent {
    data object BackClick : UserProfileNavAction
    data class PostClick(val postId: Long) : UserProfileNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : UserProfileNavAction
    data class AuthorClick(val userId: Long) : UserProfileNavAction
    data object EditProfileClick : UserProfileNavAction
    data object FollowingClick : UserProfileNavAction
    data object FollowersClick : UserProfileNavAction
    data class MessageClick(val userId: Long, val displayName: String) : UserProfileNavAction
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
    private val _userId = MutableStateFlow<Long?>(null)
    private val _refreshTrigger = MutableStateFlow(0)
    private val _postMods = MutableStateFlow<PersistentMap<Long, PostModification>>(persistentHashMapOf())

    val uiState: StateFlow<UserProfileUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        UserProfilePresenter()
    }

    private val pagingTrigger = combine(_userId, _refreshTrigger) { userId, _ -> userId }

    val postsPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId == null) flowOf(PagingData.empty())
            else userRepository.userPostsPaging(userId)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val repliesPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId == null) flowOf(PagingData.empty())
            else userRepository.userRepliesPaging(userId)
        }
        .cachedIn(viewModelScope)
        .combine(_postMods) { pagingData, mods ->
            if (mods.isEmpty()) pagingData
            else pagingData.map { it.applyMods(mods) }
        }

    val likesPaging: Flow<PagingData<Post>> = pagingTrigger
        .flatMapLatest { userId ->
            if (userId == null) flowOf(PagingData.empty())
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
                    is UserProfileAction.Load -> {
                        if (state.profile?.id != action.userId) {
                            state = UserProfileUiState(
                                selectedTab = ProfileTab.POSTS,
                                screenState = UserProfileScreenState.Loading
                            )
                        }
                        loadProfile(action.userId, state)
                    }
                    UserProfileAction.Refresh -> {
                        val userId = _userId.value
                        if (userId != null) {
                            _postMods.value = persistentHashMapOf()
                            _refreshTrigger.value++
                            if (state.content == null) {
                                state = state.copy(screenState = UserProfileScreenState.Loading)
                            }
                            loadProfile(userId, state)
                        } else {
                            state
                        }
                    }
                    is UserProfileAction.SelectTab -> state.copy(selectedTab = action.tab)
                    UserProfileAction.ToggleFollow -> toggleFollow(state)
                    is UserProfileAction.ToggleLike -> handleToggleLike(action, state)
                    is UserProfileAction.ToggleBookmark -> handleToggleBookmark(action, state)
                    UserProfileAction.ErrorDismissed -> state.clearBanner()
                }
            }
        }

        return state
    }

    private suspend fun loadProfile(
        userId: Long,
        previousState: UserProfileUiState
    ): UserProfileUiState {
        _userId.value = userId
        _postMods.value = persistentHashMapOf()
        val currentUserId = authRepository.currentUserId.first()
        val shouldPreserveTab = previousState.profile?.id == userId

        return fold(
            block = { userRepository.getUserProfile(userId) },
            recover = { error ->
                previousState.toLoadFailure(
                    userId = userId,
                    currentUserId = currentUserId,
                    message = formatError(error)
                )
            },
            transform = { profile ->
                previousState.copy(
                    selectedTab = if (shouldPreserveTab) previousState.selectedTab else ProfileTab.POSTS,
                    screenState = UserProfileScreenState.Content(
                        profile = profile,
                        currentUserId = currentUserId
                    )
                )
            }
        )
    }

    private suspend fun toggleFollow(currentState: UserProfileUiState): UserProfileUiState {
        val content = currentState.content ?: return currentState
        val profile = content.profile
        if (currentState.isOwnProfile) return currentState
        if (content.isFollowLoading) return currentState

        val isCurrentlyFollowing = profile.isFollowedByCurrentUser == true

        val optimisticState = currentState.updateContent {
            copy(
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
                isFollowLoading = true,
                bannerMessage = null
            )
        }

        return fold(
            block = {
                if (isCurrentlyFollowing) {
                    userRepository.unfollowUser(profile.id)
                } else {
                    userRepository.followUser(profile.id)
                }
            },
            recover = { error ->
                optimisticState.updateContent {
                    copy(
                        profile = profile,
                        isFollowLoading = false,
                        bannerMessage = formatError(error)
                    )
                }
            },
            transform = {
                optimisticState.updateContent {
                    copy(isFollowLoading = false)
                }
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
                currentState.withBanner(formatPostError(error))
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
        action: UserProfileAction.ToggleBookmark,
        currentState: UserProfileUiState
    ): UserProfileUiState {
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
                currentState.withBanner(formatPostError(error))
            },
            transform = { currentState }
        )
    }

    private fun UserProfileUiState.updateContent(
        transform: UserProfileScreenState.Content.() -> UserProfileScreenState.Content
    ): UserProfileUiState {
        val currentContent = content ?: return this
        return copy(screenState = currentContent.transform())
    }

    private fun UserProfileUiState.withBanner(message: String): UserProfileUiState {
        return updateContent {
            copy(bannerMessage = message)
        }
    }

    private fun UserProfileUiState.clearBanner(): UserProfileUiState {
        val currentContent = content ?: return this
        if (currentContent.bannerMessage == null) return this
        return copy(
            screenState = currentContent.copy(bannerMessage = null)
        )
    }

    private fun UserProfileUiState.toLoadFailure(
        userId: Long,
        currentUserId: Long?,
        message: String
    ): UserProfileUiState {
        val currentContent = content
            ?.takeIf { it.profile.id == userId }
            ?: return copy(
            screenState = UserProfileScreenState.Error(message = message)
        )
        return copy(
            screenState = currentContent.copy(
                currentUserId = currentUserId,
                bannerMessage = message
            )
        )
    }

    private fun Post.applyMods(mods: PersistentMap<Long, PostModification>): Post {
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
