package com.connor.kwitter.features.postdetail

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
import arrow.core.raise.context.Raise
import arrow.core.raise.fold
import com.connor.kwitter.domain.notification.repository.NotificationRepository
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostMutationEvent
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ThreadReplyItem(
    val post: Post,
    val depth: Int
)

sealed interface PostDetailScreenState {
    data object Loading : PostDetailScreenState
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : PostDetailScreenState
    data class Content(
        val post: Post,
        val threadReplies: List<ThreadReplyItem> = emptyList(),
        val bannerMessage: String? = null
    ) : PostDetailScreenState
}

data class PostDetailUiState(
    val screenState: PostDetailScreenState = PostDetailScreenState.Loading
) {
    val content: PostDetailScreenState.Content?
        get() = screenState as? PostDetailScreenState.Content

    val post: Post?
        get() = content?.post

    val threadReplies: List<ThreadReplyItem>
        get() = content?.threadReplies.orEmpty()

    val bannerMessage: String?
        get() = content?.bannerMessage
}

sealed interface PostDetailIntent

sealed interface PostDetailAction : PostDetailIntent {
    data class Load(val postId: Long) : PostDetailAction
    data object Refresh : PostDetailAction
    data object ErrorDismissed : PostDetailAction
    data class ToggleLike(val postId: Long) : PostDetailAction
    data class ToggleBookmark(val postId: Long) : PostDetailAction
}

sealed interface PostDetailNavAction : PostDetailIntent {
    data class ReplyClick(
        val postId: Long,
        val authorName: String,
        val content: String,
        val avatarUrl: String? = null
    ) : PostDetailNavAction
    data object BackClick : PostDetailNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : PostDetailNavAction
    data class AuthorClick(val userId: Long) : PostDetailNavAction
}

class PostDetailViewModel(
    private val postRepository: PostRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private companion object {
        const val THREAD_PAGE_SIZE = 20
    }

    private val _events = Channel<PostDetailAction>(Channel.UNLIMITED)
    private var currentPostId: Long? = null
    private var currentThreadPostIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            postRepository.postMutations.collect { event ->
                when (event) {
                    is PostMutationEvent.PostCreated -> {
                        val postId = currentPostId ?: return@collect
                        val parentId = event.parentId ?: return@collect
                        val affectsCurrentThread =
                            parentId == postId || parentId in currentThreadPostIds

                        if (affectsCurrentThread) {
                            _events.trySend(PostDetailAction.Refresh)
                        }
                    }
                }
            }
        }
    }

    val uiState: StateFlow<PostDetailUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        PostDetailPresenter()
    }

    fun onEvent(event: PostDetailAction) {
        if (event is PostDetailAction.Load) {
            currentPostId = event.postId
        }
        _events.trySend(event)
    }

    @Composable
    private fun PostDetailPresenter(): PostDetailUiState {
        var state by remember { mutableStateOf(PostDetailUiState()) }

        LaunchedEffect(currentPostId) {
            val postId = currentPostId ?: return@LaunchedEffect
            notificationRepository.observePostLikeEvents(postId).collect { event ->
                if (event.postId == postId || event.postId in currentThreadPostIds) {
                    state = updatePostInState(state, event.postId) {
                        copy(
                            isLikedByCurrentUser = event.isLiked,
                            stats = stats.copy(likeCount = event.newLikeCount)
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is PostDetailAction.Load -> {
                        if (state.post?.id != action.postId) {
                            state = PostDetailUiState(
                                screenState = PostDetailScreenState.Loading
                            )
                        }
                        loadPostDetail(action.postId, state)
                    }
                    PostDetailAction.Refresh -> {
                        currentPostId?.let { postId ->
                            if (state.content == null) {
                                state = PostDetailUiState(
                                    screenState = PostDetailScreenState.Loading
                                )
                            }
                            loadPostDetail(postId, state)
                        } ?: state
                    }
                    PostDetailAction.ErrorDismissed -> state.clearBanner()
                    is PostDetailAction.ToggleLike -> handleToggleLike(action.postId, state)
                    is PostDetailAction.ToggleBookmark -> handleToggleBookmark(action.postId, state)
                }
            }
        }

        return state
    }

    private suspend fun loadPostDetail(
        postId: Long,
        previousState: PostDetailUiState
    ): PostDetailUiState {
        val previousContent = previousState.content
        val fallbackReplies = previousContent
            ?.takeIf { it.post.id == postId }
            ?.threadReplies
            .orEmpty()

        val resolvedState = fold(
            block = { postRepository.getPost(postId) },
            recover = { error ->
                previousState.toLoadFailure(
                    postId = postId,
                    message = formatError(error)
                )
            },
            transform = { post ->
                fold(
                    block = { loadReplyThread(postId) },
                    recover = { error ->
                        previousState.copy(
                            screenState = PostDetailScreenState.Content(
                                post = post,
                                threadReplies = fallbackReplies,
                                bannerMessage = formatError(error)
                            )
                        )
                    },
                    transform = { replies ->
                        previousState.copy(
                            screenState = PostDetailScreenState.Content(
                                post = post,
                                threadReplies = replies
                            )
                        )
                    }
                )
            }
        )

        currentThreadPostIds = buildThreadPostIds(resolvedState.content)
        return resolvedState
    }

    private fun buildThreadPostIds(
        content: PostDetailScreenState.Content?
    ): Set<Long> = buildSet {
        content?.post?.id?.let(::add)
        content?.threadReplies?.forEach { add(it.post.id) }
    }

    context(_: Raise<PostError>)
    private suspend fun loadReplyThread(
        rootPostId: Long
    ): List<ThreadReplyItem> =
        loadThreadBranch(
            parentId = rootPostId,
            depth = 0,
            visited = mutableSetOf()
        )

    context(_: Raise<PostError>)
    private suspend fun loadThreadBranch(
        parentId: Long,
        depth: Int,
        visited: MutableSet<Long>
    ): List<ThreadReplyItem> {
        if (!visited.add(parentId)) {
            return emptyList()
        }

        val directReplies = fetchAllReplies(parentId)
        val flattenedReplies = mutableListOf<ThreadReplyItem>()

        for (reply in directReplies) {
            flattenedReplies.add(
                ThreadReplyItem(
                    post = reply,
                    depth = depth
                )
            )
            flattenedReplies.addAll(
                loadThreadBranch(
                    parentId = reply.id,
                    depth = depth + 1,
                    visited = visited
                )
            )
        }

        return flattenedReplies
    }

    context(_: Raise<PostError>)
    private suspend fun fetchAllReplies(parentId: Long): List<Post> {
        val allReplies = mutableListOf<Post>()
        var offset = 0
        var hasMore = true

        while (hasMore) {
            val page = postRepository.getReplies(
                postId = parentId,
                query = PostPageQuery(
                    limit = THREAD_PAGE_SIZE,
                    offset = offset
                )
            )

            allReplies.addAll(page.posts)
            offset += page.posts.size
            hasMore = page.hasMore && page.posts.isNotEmpty()
        }

        return allReplies
    }

    private fun formatError(error: PostError): String = when (error) {
        is PostError.NetworkError -> "Network error: ${error.message}"
        is PostError.ServerError -> "Server error (${error.code}): ${error.message}"
        is PostError.ClientError -> "Request error (${error.code}): ${error.message}"
        is PostError.Unauthorized -> "Authentication required"
        is PostError.NotFound -> "Post not found"
        is PostError.Unknown -> "Unknown error: ${error.message}"
    }

    private suspend fun handleToggleLike(
        postId: Long,
        currentState: PostDetailUiState
    ): PostDetailUiState {
        val targetPost = findPost(postId, currentState) ?: return currentState
        val isCurrentlyLiked = targetPost.isLikedByCurrentUser == true

        val optimisticState = updatePostInState(currentState, postId) {
            copy(
                isLikedByCurrentUser = !isCurrentlyLiked,
                stats = stats.copy(
                    likeCount = if (isCurrentlyLiked) stats.likeCount - 1 else stats.likeCount + 1
                )
            )
        }

        return fold(
            block = {
                if (isCurrentlyLiked) {
                    postRepository.unlikePost(postId)
                } else {
                    postRepository.likePost(postId)
                }
            },
            recover = { error ->
                updatePostInState(optimisticState, postId) {
                    copy(
                        isLikedByCurrentUser = isCurrentlyLiked,
                        stats = stats.copy(
                            likeCount = if (isCurrentlyLiked) stats.likeCount + 1 else stats.likeCount - 1
                        )
                    )
                }.withBanner(formatError(error))
            },
            transform = { updatedStats ->
                updatePostInState(optimisticState, postId) {
                    copy(stats = updatedStats)
                }
            }
        )
    }

    private suspend fun handleToggleBookmark(
        postId: Long,
        currentState: PostDetailUiState
    ): PostDetailUiState {
        val targetPost = findPost(postId, currentState) ?: return currentState
        val isCurrentlyBookmarked = targetPost.isBookmarkedByCurrentUser == true

        val optimisticState = updatePostInState(currentState, postId) {
            copy(isBookmarkedByCurrentUser = !isCurrentlyBookmarked)
        }

        return fold(
            block = {
                if (isCurrentlyBookmarked) {
                    postRepository.unbookmarkPost(postId)
                } else {
                    postRepository.bookmarkPost(postId)
                }
            },
            recover = { error ->
                updatePostInState(optimisticState, postId) {
                    copy(isBookmarkedByCurrentUser = isCurrentlyBookmarked)
                }.withBanner(formatError(error))
            },
            transform = { optimisticState }
        )
    }

    private fun findPost(postId: Long, state: PostDetailUiState): Post? {
        val content = state.content ?: return null
        if (content.post.id == postId) return content.post
        return content.threadReplies.find { it.post.id == postId }?.post
    }

    private fun updatePostInState(
        state: PostDetailUiState,
        postId: Long,
        transform: Post.() -> Post
    ): PostDetailUiState {
        val content = state.content ?: return state
        val updatedPost = if (content.post.id == postId) {
            content.post.transform()
        } else {
            content.post
        }
        val replyIndex = content.threadReplies.indexOfFirst { it.post.id == postId }
        val updatedReplies = if (replyIndex >= 0) {
            content.threadReplies.toMutableList().apply {
                val currentReply = this[replyIndex]
                this[replyIndex] = currentReply.copy(post = currentReply.post.transform())
            }
        } else {
            content.threadReplies
        }

        if (updatedPost === content.post && updatedReplies === content.threadReplies) {
            return state
        }

        return state.copy(
            screenState = content.copy(
                post = updatedPost,
                threadReplies = updatedReplies
            )
        )
    }

    private fun PostDetailUiState.toLoadFailure(
        postId: Long,
        message: String
    ): PostDetailUiState {
        val currentContent = content
            ?.takeIf { it.post.id == postId }
            ?: return copy(
            screenState = PostDetailScreenState.Error(message = message)
        )
        return copy(
            screenState = currentContent.copy(bannerMessage = message)
        )
    }

    private fun PostDetailUiState.withBanner(message: String): PostDetailUiState {
        val currentContent = content ?: return this
        return copy(
            screenState = currentContent.copy(bannerMessage = message)
        )
    }

    private fun PostDetailUiState.clearBanner(): PostDetailUiState {
        val currentContent = content ?: return this
        if (currentContent.bannerMessage == null) return this
        return copy(
            screenState = currentContent.copy(bannerMessage = null)
        )
    }
}
