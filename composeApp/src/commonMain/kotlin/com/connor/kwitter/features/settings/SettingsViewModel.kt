package com.connor.kwitter.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.features.auth.AuthUiError
import com.connor.kwitter.features.auth.toAuthUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsInfoMessage {
    data object ChangePasswordInDevelopment : SettingsInfoMessage
}

data class SettingsUiState(
    val allowDefaultPm: Boolean = true,
    val isLogoutDialogVisible: Boolean = false,
    val isLoggingOut: Boolean = false,
    val infoMessage: SettingsInfoMessage? = null,
    val error: AuthUiError? = null
)

sealed interface SettingsIntent

sealed interface SettingsAction : SettingsIntent {
    data class AllowDefaultPmChanged(val enabled: Boolean) : SettingsAction
    data object ChangePasswordClick : SettingsAction
    data object ChangePasswordMessageConsumed : SettingsAction
    data object LogoutClick : SettingsAction
    data object LogoutDismiss : SettingsAction
    data object LogoutConfirm : SettingsAction
    data object ErrorDismissed : SettingsAction
}

class SettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onEvent(action: SettingsAction) {
        when (action) {
            is SettingsAction.AllowDefaultPmChanged -> {
                _uiState.update { it.copy(allowDefaultPm = action.enabled) }
            }
            SettingsAction.ChangePasswordClick -> {
                _uiState.update {
                    it.copy(infoMessage = SettingsInfoMessage.ChangePasswordInDevelopment)
                }
            }
            SettingsAction.ChangePasswordMessageConsumed -> {
                _uiState.update { it.copy(infoMessage = null) }
            }
            SettingsAction.LogoutClick -> {
                _uiState.update { it.copy(isLogoutDialogVisible = true) }
            }
            SettingsAction.LogoutDismiss -> {
                _uiState.update { it.copy(isLogoutDialogVisible = false) }
            }
            SettingsAction.LogoutConfirm -> {
                performLogout()
            }
            SettingsAction.ErrorDismissed -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }

    private fun performLogout() {
        val state = _uiState.value
        if (state.isLoggingOut) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoggingOut = true,
                    isLogoutDialogVisible = false,
                    error = null
                )
            }

            val result = authRepository.logout()
            result.fold(
                ifLeft = { error ->
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            error = formatAuthError(error)
                        )
                    }
                },
                ifRight = {
                    _uiState.update { current ->
                        current.copy(isLoggingOut = false)
                    }
                }
            )
        }
    }

    private fun formatAuthError(error: AuthError): AuthUiError =
        error.toAuthUiError(includeInvalidDetail = true)
}
