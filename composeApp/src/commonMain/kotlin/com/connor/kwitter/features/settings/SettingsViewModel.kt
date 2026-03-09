package com.connor.kwitter.features.settings

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
import com.connor.kwitter.core.result.Result
import com.connor.kwitter.core.result.resultFlow
import com.connor.kwitter.core.result.uiResultOf
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.features.auth.AuthUiError
import com.connor.kwitter.features.auth.toAuthUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

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

    private val events = Channel<SettingsAction>(Channel.UNLIMITED)

    val uiState: StateFlow<SettingsUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        SettingsPresenter()
    }

    fun onEvent(action: SettingsAction) {
        events.trySend(action)
    }

    @Composable
    private fun SettingsPresenter(): SettingsUiState {
        var state by remember { mutableStateOf(SettingsUiState()) }

        LaunchedEffect(Unit) {
            events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is SettingsAction.AllowDefaultPmChanged -> {
                        state.copy(allowDefaultPm = action.enabled)
                    }
                    SettingsAction.ChangePasswordClick -> {
                        state.copy(infoMessage = SettingsInfoMessage.ChangePasswordInDevelopment)
                    }
                    SettingsAction.ChangePasswordMessageConsumed -> {
                        state.copy(infoMessage = null)
                    }
                    SettingsAction.LogoutClick -> {
                        state.copy(isLogoutDialogVisible = true)
                    }
                    SettingsAction.LogoutDismiss -> {
                        state.copy(isLogoutDialogVisible = false)
                    }
                    SettingsAction.LogoutConfirm -> {
                        if (state.isLoggingOut) {
                            state
                        } else {
                            resultFlow(
                                mapError = ::formatAuthError
                            ) {
                                authRepository.logout()
                            }.collect { result ->
                                state = when (result) {
                                    Result.Loading -> state.copy(
                                        isLoggingOut = true,
                                        isLogoutDialogVisible = false,
                                        error = null
                                    )
                                    is Result.Success -> state.copy(
                                        isLoggingOut = false,
                                        error = null
                                    )
                                    is Result.Error -> state.copy(
                                        isLoggingOut = false,
                                        error = result.error
                                    )
                                }
                            }
                            state
                        }
                    }
                    SettingsAction.ErrorDismissed -> {
                        state.copy(error = null)
                    }
                }
            }
        }

        return state
    }

    private fun formatAuthError(error: AuthError): AuthUiError =
        error.toAuthUiError(includeInvalidDetail = true)
}
