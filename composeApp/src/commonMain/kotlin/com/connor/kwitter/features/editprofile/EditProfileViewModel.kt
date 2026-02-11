package com.connor.kwitter.features.editprofile

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
import com.connor.kwitter.domain.user.model.UpdateProfileRequest
import com.connor.kwitter.domain.user.model.UserError
import com.connor.kwitter.domain.user.repository.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val pendingCropImageBytes: ByteArray? = null,
    val croppedAvatarBytes: ByteArray? = null,
    val error: String? = null
)

sealed interface EditProfileIntent

sealed interface EditProfileAction : EditProfileIntent {
    data class Load(val userId: String) : EditProfileAction
    data class DisplayNameChanged(val value: String) : EditProfileAction
    data class UsernameChanged(val value: String) : EditProfileAction
    data class BioChanged(val value: String) : EditProfileAction
    data class AvatarSelected(val media: SelectedMedia) : EditProfileAction
    data class AvatarCropConfirmed(val croppedBytes: ByteArray) : EditProfileAction
    data object AvatarCropCancelled : EditProfileAction
    data object Save : EditProfileAction
    data object ErrorDismissed : EditProfileAction
}

sealed interface EditProfileNavAction : EditProfileIntent {
    data object BackClick : EditProfileNavAction
    data object SaveSuccess : EditProfileNavAction
}

class EditProfileViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _events = Channel<EditProfileAction>(Channel.UNLIMITED)

    val uiState: StateFlow<EditProfileUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        EditProfilePresenter()
    }

    fun onEvent(event: EditProfileAction) {
        _events.trySend(event)
    }

    @Composable
    private fun EditProfilePresenter(): EditProfileUiState {
        var state by remember { mutableStateOf(EditProfileUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is EditProfileAction.Load -> loadProfile(action.userId, state)
                    is EditProfileAction.DisplayNameChanged -> state.copy(displayName = action.value)
                    is EditProfileAction.UsernameChanged -> state.copy(username = action.value)
                    is EditProfileAction.BioChanged -> state.copy(bio = action.value)
                    is EditProfileAction.AvatarSelected -> {
                        viewModelScope.launch {
                            val bytes = action.media.readBytes()
                            state = state.copy(pendingCropImageBytes = bytes)
                        }
                        state
                    }
                    is EditProfileAction.AvatarCropConfirmed -> {
                        state = state.copy(
                            croppedAvatarBytes = action.croppedBytes,
                            pendingCropImageBytes = null,
                            isUploadingAvatar = true
                        )
                        viewModelScope.launch {
                            userRepository.uploadAvatar(
                                bytes = action.croppedBytes,
                                fileName = "avatar.jpg",
                                mimeType = "image/jpeg"
                            ).fold(
                                ifLeft = { error ->
                                    state = state.copy(
                                        isUploadingAvatar = false,
                                        croppedAvatarBytes = null,
                                        error = formatError(error)
                                    )
                                },
                                ifRight = { avatarUrl ->
                                    state = state.copy(
                                        isUploadingAvatar = false,
                                        avatarUrl = avatarUrl
                                    )
                                }
                            )
                        }
                        state
                    }
                    is EditProfileAction.AvatarCropCancelled -> {
                        state.copy(pendingCropImageBytes = null)
                    }
                    is EditProfileAction.Save -> saveProfile(state)
                    is EditProfileAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private suspend fun loadProfile(
        userId: String,
        currentState: EditProfileUiState
    ): EditProfileUiState {
        val loadingState = currentState.copy(isLoading = true, error = null)

        return userRepository.getUserProfile(userId).fold(
            ifLeft = { error ->
                loadingState.copy(
                    isLoading = false,
                    error = formatError(error)
                )
            },
            ifRight = { profile ->
                loadingState.copy(
                    isLoading = false,
                    displayName = profile.displayName,
                    username = profile.username,
                    bio = profile.bio,
                    avatarUrl = profile.avatarUrl.orEmpty()
                )
            }
        )
    }

    private suspend fun saveProfile(currentState: EditProfileUiState): EditProfileUiState {
        if (currentState.isSaving || currentState.isUploadingAvatar) return currentState

        val username = currentState.username.trim()
        val displayName = currentState.displayName.trim()
        val bio = currentState.bio.trim()

        if (username.isBlank()) {
            return currentState.copy(error = "Username cannot be blank")
        }
        if (displayName.isBlank()) {
            return currentState.copy(error = "Display name cannot be blank")
        }

        val savingState = currentState.copy(isSaving = true, error = null)

        return userRepository.updateCurrentUserProfile(
            UpdateProfileRequest(
                username = username,
                displayName = displayName,
                bio = bio
            )
        ).fold(
            ifLeft = { error ->
                savingState.copy(
                    isSaving = false,
                    error = formatError(error)
                )
            },
            ifRight = {
                savingState.copy(isSaving = false, isSuccess = true)
            }
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
