package com.connor.kwitter.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connor.kwitter.core.result.Result
import com.connor.kwitter.core.result.asResult
import com.connor.kwitter.core.result.uiResultOf
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.features.auth.AuthUiError
import com.connor.kwitter.features.auth.toAuthUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
) {
    val logoutResult: Result<Unit, AuthUiError>
        get() = uiResultOf(isLoading = isLoggingOut, error = error)
}

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

            flow {
                emit(authRepository.logout())
            }.asResult(::formatAuthError).collect { result ->
                _uiState.update { current ->
                    when (result) {
                        Result.Loading -> current.copy(
                            isLoggingOut = true,
                            error = null
                        )
                        is Result.Success -> current.copy(
                            isLoggingOut = false,
                            error = null
                        )
                        is Result.Error -> current.copy(
                            isLoggingOut = false,
                            error = result.error
                        )
                    }
                }
            }
        }
    }

    private fun formatAuthError(error: AuthError): AuthUiError =
        error.toAuthUiError(includeInvalidDetail = true)
}
