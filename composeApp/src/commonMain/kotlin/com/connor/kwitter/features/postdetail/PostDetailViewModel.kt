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
import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.post.model.PostPageQuery
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostMutationEvent
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ThreadReplyItem(
    val post: Post,
    val depth: Int
)

data class PostDetailUiState(
    val post: Post? = null,
    val threadReplies: List<ThreadReplyItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface PostDetailIntent

sealed interface PostDetailAction : PostDetailIntent {
    data class Load(val postId: String) : PostDetailAction
    data class Refresh(val postId: String) : PostDetailAction
    data object ErrorDismissed : PostDetailAction
    data class ToggleLike(val postId: String) : PostDetailAction
    data class ToggleBookmark(val postId: String) : PostDetailAction
}

sealed interface PostDetailNavAction : PostDetailIntent {
    data class ReplyClick(
        val postId: String,
        val authorName: String,
        val content: String
    ) : PostDetailNavAction
    data object BackClick : PostDetailNavAction
    data class MediaClick(val media: List<PostMedia>, val index: Int) : PostDetailNavAction
    data class AuthorClick(val userId: String) : PostDetailNavAction
}

class PostDetailViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    private companion object {
        const val THREAD_PAGE_SIZE = 20
    }

    private val _events = Channel<PostDetailAction>(Channel.UNLIMITED)
    private var currentPostId: String? = null
    private var currentThreadPostIds: Set<String> = emptySet()

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
                            _events.trySend(PostDetailAction.Refresh(postId))
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
        when (event) {
            is PostDetailAction.Load -> currentPostId = event.postId
            is PostDetailAction.Refresh -> currentPostId = event.postId
            else -> Unit
        }
        _events.trySend(event)
    }

    @Composable
    private fun PostDetailPresenter(): PostDetailUiState {
        var state by remember { mutableStateOf(PostDetailUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is PostDetailAction.Load -> loadPostDetail(action.postId, state)
                    is PostDetailAction.Refresh -> loadPostDetail(action.postId, state)
                    is PostDetailAction.ErrorDismissed -> state.copy(error = null)
                    is PostDetailAction.ToggleLike -> handleToggleLike(action.postId, state)
                    is PostDetailAction.ToggleBookmark -> handleToggleBookmark(action.postId, state)
                }
            }
        }

        return state
    }

    private suspend fun loadPostDetail(
        postId: String,
        previousState: PostDetailUiState
    ): PostDetailUiState {
        val loadingState = previousState.copy(isLoading = true, error = null)
        val postResult = postRepository.getPost(postId)

        val resolvedState = postResult.fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoading = false,
                    post = null,
                    threadReplies = emptyList(),
                    error = formatError(error)
                )
            },
            ifRight = { post ->
                loadReplyThread(postId).fold(
                    ifLeft = { error ->
                        loadingState.copy(
                            isLoading = false,
                            post = post,
                            threadReplies = emptyList(),
                            error = formatError(error)
                        )
                    },
                    ifRight = { replies ->
                        loadingState.copy(
                            isLoading = false,
                            post = post,
                            threadReplies = replies
                        )
                    }
                )
            }
        )

        currentThreadPostIds = buildThreadPostIds(resolvedState)
        return resolvedState
    }

    private fun buildThreadPostIds(state: PostDetailUiState): Set<String> = buildSet {
        state.post?.id?.let(::add)
        state.threadReplies.forEach { add(it.post.id) }
    }

    private suspend fun loadReplyThread(rootPostId: String): Either<PostError, List<ThreadReplyItem>> = either {
        loadThreadBranch(
            parentId = rootPostId,
            depth = 0,
            visited = mutableSetOf()
        ).bind()
    }

    private suspend fun loadThreadBranch(
        parentId: String,
        depth: Int,
        visited: MutableSet<String>
    ): Either<PostError, List<ThreadReplyItem>> = either {
        if (!visited.add(parentId)) {
            return@either emptyList()
        }

        val directReplies = fetchAllReplies(parentId).bind()
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
                ).bind()
            )
        }

        flattenedReplies
    }

    private suspend fun fetchAllReplies(parentId: String): Either<PostError, List<Post>> = either {
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
            ).bind()

            allReplies.addAll(page.posts)
            offset += page.posts.size
            hasMore = page.hasMore && page.posts.isNotEmpty()
        }

        allReplies
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
        postId: String,
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

        val result = if (isCurrentlyLiked) {
            postRepository.unlikePost(postId)
        } else {
            postRepository.likePost(postId)
        }

        return result.fold(
            ifLeft = { error ->
                updatePostInState(optimisticState, postId) {
                    copy(
                        isLikedByCurrentUser = isCurrentlyLiked,
                        stats = stats.copy(
                            likeCount = if (isCurrentlyLiked) stats.likeCount + 1 else stats.likeCount - 1
                        )
                    )
                }.copy(error = formatError(error))
            },
            ifRight = { updatedStats ->
                updatePostInState(optimisticState, postId) {
                    copy(stats = updatedStats)
                }
            }
        )
    }

    private suspend fun handleToggleBookmark(
        postId: String,
        currentState: PostDetailUiState
    ): PostDetailUiState {
        val targetPost = findPost(postId, currentState) ?: return currentState
        val isCurrentlyBookmarked = targetPost.isBookmarkedByCurrentUser == true

        val optimisticState = updatePostInState(currentState, postId) {
            copy(isBookmarkedByCurrentUser = !isCurrentlyBookmarked)
        }

        val result = if (isCurrentlyBookmarked) {
            postRepository.unbookmarkPost(postId)
        } else {
            postRepository.bookmarkPost(postId)
        }

        return result.fold(
            ifLeft = { error ->
                updatePostInState(optimisticState, postId) {
                    copy(isBookmarkedByCurrentUser = isCurrentlyBookmarked)
                }.copy(error = formatError(error))
            },
            ifRight = { optimisticState }
        )
    }

    private fun findPost(postId: String, state: PostDetailUiState): Post? {
        if (state.post?.id == postId) return state.post
        return state.threadReplies.find { it.post.id == postId }?.post
    }

    private fun updatePostInState(
        state: PostDetailUiState,
        postId: String,
        transform: Post.() -> Post
    ): PostDetailUiState {
        val updatedPost = if (state.post?.id == postId) {
            state.post.transform()
        } else {
            state.post
        }
        val updatedReplies = state.threadReplies.map { reply ->
            if (reply.post.id == postId) reply.copy(post = reply.post.transform())
            else reply
        }
        return state.copy(post = updatedPost, threadReplies = updatedReplies)
    }
}
