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
)

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
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading

                        val result = authRepository.login(
                            email = loading.email,
                            password = loading.password
                        )

                        result.fold(
                            ifLeft = { error ->
                                loading.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = {
                                loading.copy(isLoading = false, loginSuccess = true)
                            }
                        )
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
