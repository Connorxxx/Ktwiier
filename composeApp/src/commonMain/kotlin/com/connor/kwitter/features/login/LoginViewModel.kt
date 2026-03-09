package com.connor.kwitter.features.login

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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val loginSuccess: Boolean = false
) {
    val authResult: Result<Unit, AuthUiError>
        get() = uiResultOf(isLoading = isLoading, error = error)
}

sealed interface LoginIntent

sealed interface LoginAction : LoginIntent {
    data class EmailChanged(val email: String) : LoginAction
    data class PasswordChanged(val password: String) : LoginAction
    data object LoginClicked : LoginAction
    data object ErrorDismissed : LoginAction
}

sealed interface LoginNavAction : LoginIntent {
    data object OnLoginSuccess : LoginNavAction
    data object RegisterClick : LoginNavAction
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<LoginAction>(Channel.UNLIMITED)

    val uiState: StateFlow<LoginUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        LoginPresenter()
    }

    fun onEvent(event: LoginAction) {
        _events.trySend(event)
    }

    @Composable
    private fun LoginPresenter(): LoginUiState {
        var state by remember { mutableStateOf(LoginUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is LoginAction.EmailChanged -> state.copy(email = action.email)
                    is LoginAction.PasswordChanged -> state.copy(password = action.password)
                    is LoginAction.LoginClicked -> {
                        resultFlow(
                            mapError = ::formatError
                        ) {
                                authRepository.login(
                                    email = state.email,
                                    password = state.password
                                )
                        }.collect { result ->
                            state = when (result) {
                                Result.Loading -> state.copy(isLoading = true, error = null)
                                is Result.Success -> state.copy(
                                    isLoading = false,
                                    error = null,
                                    loginSuccess = true
                                )
                                is Result.Error -> state.copy(
                                    isLoading = false,
                                    error = result.error
                                )
                            }
                        }
                        state
                    }
                    is LoginAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: AuthError): AuthUiError =
        error.toAuthUiError(includeInvalidDetail = false)
}
