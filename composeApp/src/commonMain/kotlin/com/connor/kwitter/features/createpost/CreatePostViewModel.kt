package com.connor.kwitter.features.createpost

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
import com.connor.kwitter.core.media.SelectedMedia
import com.connor.kwitter.core.media.readBytes
import com.connor.kwitter.domain.media.model.MediaError
import com.connor.kwitter.domain.media.repository.MediaRepository
import com.connor.kwitter.domain.post.model.CreatePostRequest
import com.connor.kwitter.domain.post.model.PostError
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostMediaType
import com.connor.kwitter.domain.post.repository.PostRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val content: String = "",
    val parentId: String? = null,
    val replyTargetAuthorName: String? = null,
    val replyTargetContent: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val selectedMedia: List<SelectedMedia> = emptyList(),
    val uploadedMedia: List<PostMedia> = emptyList(),
    val isUploading: Boolean = false
)

sealed interface CreatePostIntent

sealed interface CreatePostAction : CreatePostIntent {
    data class ContentChanged(val content: String) : CreatePostAction
    data class SetReplyTarget(
        val parentId: String?,
        val authorName: String? = null,
        val content: String? = null
    ) : CreatePostAction
    data object SubmitClicked : CreatePostAction
    data object ErrorDismissed : CreatePostAction
    data class MediaSelected(val media: List<SelectedMedia>) : CreatePostAction
    data class RemoveMedia(val index: Int) : CreatePostAction
}

sealed interface CreatePostNavAction : CreatePostIntent {
    data object OnPostCreated : CreatePostNavAction
    data object BackClick : CreatePostNavAction
}

class CreatePostViewModel(
    private val postRepository: PostRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _events = Channel<CreatePostAction>(Channel.UNLIMITED)

    val uiState: StateFlow<CreatePostUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        CreatePostPresenter()
    }

    fun onEvent(event: CreatePostAction) {
        _events.trySend(event)
    }

    @Composable
    private fun CreatePostPresenter(): CreatePostUiState {
        var state by remember { mutableStateOf(CreatePostUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is CreatePostAction.ContentChanged -> state.copy(content = action.content)
                    is CreatePostAction.SetReplyTarget -> state.copy(
                        parentId = action.parentId,
                        replyTargetAuthorName = action.authorName,
                        replyTargetContent = action.content
                    )
                    is CreatePostAction.MediaSelected -> {
                        val maxAllowed = 4 - state.selectedMedia.size
                        val newMedia = action.media.take(maxAllowed)
                        if (newMedia.isEmpty()) {
                            state
                        } else {
                            val updatedSelected = state.selectedMedia + newMedia
                            state = state.copy(
                                selectedMedia = updatedSelected,
                                isUploading = true
                            )
                            // Upload each new media file
                            newMedia.forEach { media ->
                                viewModelScope.launch {
                                    val bytes = media.readBytes()
                                    val result = mediaRepository.uploadMedia(
                                        bytes = bytes,
                                        fileName = media.name,
                                        mimeType = media.mimeType
                                    )
                                    result.fold(
                                        ifLeft = { error ->
                                            state = state.copy(
                                                error = formatMediaError(error),
                                                selectedMedia = state.selectedMedia - media,
                                                isUploading = state.selectedMedia.size > state.uploadedMedia.size + 1
                                            )
                                        },
                                        ifRight = { response ->
                                            val postMedia = PostMedia(
                                                url = response.url,
                                                type = if (response.type == "VIDEO") PostMediaType.VIDEO else PostMediaType.IMAGE
                                            )
                                            state = state.copy(
                                                uploadedMedia = state.uploadedMedia + postMedia,
                                                isUploading = state.selectedMedia.size > state.uploadedMedia.size + 1
                                            )
                                        }
                                    )
                                }
                            }
                            state
                        }
                    }
                    is CreatePostAction.RemoveMedia -> {
                        if (action.index in state.selectedMedia.indices) {
                            val newSelected = state.selectedMedia.toMutableList().apply { removeAt(action.index) }
                            val newUploaded = if (action.index in state.uploadedMedia.indices) {
                                state.uploadedMedia.toMutableList().apply { removeAt(action.index) }
                            } else {
                                state.uploadedMedia
                            }
                            state.copy(
                                selectedMedia = newSelected,
                                uploadedMedia = newUploaded
                            )
                        } else {
                            state
                        }
                    }
                    is CreatePostAction.SubmitClicked -> {
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading
                        val result = postRepository.createPost(
                            CreatePostRequest(
                                content = loading.content,
                                mediaUrls = loading.uploadedMedia,
                                parentId = loading.parentId
                            )
                        )
                        result.fold(
                            ifLeft = { error ->
                                loading.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = {
                                loading.copy(isLoading = false, isSuccess = true)
                            }
                        )
                    }
                    is CreatePostAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: PostError): String = when (error) {
        is PostError.NetworkError -> "Network error: ${error.message}"
        is PostError.ServerError -> "Server error (${error.code}): ${error.message}"
        is PostError.ClientError -> "Request error (${error.code}): ${error.message}"
        is PostError.Unauthorized -> "Authentication required"
        is PostError.NotFound -> "Not found"
        is PostError.Unknown -> "Unknown error: ${error.message}"
    }

    private fun formatMediaError(error: MediaError): String = when (error) {
        is MediaError.NetworkError -> "Upload failed: ${error.message}"
        is MediaError.ServerError -> "Upload failed (${error.code}): ${error.message}"
        is MediaError.ClientError -> "Upload failed (${error.code}): ${error.message}"
        is MediaError.Unauthorized -> "Authentication required"
        is MediaError.NotFound -> "Upload endpoint not found"
        is MediaError.Unknown -> "Upload failed: ${error.message}"
    }
}
