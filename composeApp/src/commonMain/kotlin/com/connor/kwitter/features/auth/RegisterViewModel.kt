package com.connor.kwitter.features.auth

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
import com.connor.kwitter.features.auth.toAuthUiError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class RegisterUiState(
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val registerSuccess: Boolean = false
) {
    val registerResult: Result<Unit, AuthUiError>
        get() = uiResultOf(isLoading = isLoading, error = error)
}

sealed interface RegisterIntent

sealed interface RegisterAction : RegisterIntent {
    data class EmailChanged(val email: String) : RegisterAction
    data class NameChanged(val name: String) : RegisterAction
    data class PasswordChanged(val password: String) : RegisterAction
    data object RegisterClicked : RegisterAction
    data object ErrorDismissed : RegisterAction
}

sealed interface RegisterNavAction : RegisterIntent {
    data object OnRegisterSuccess : RegisterNavAction
    data object LoginClick : RegisterNavAction
}

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _events = Channel<RegisterAction>(Channel.UNLIMITED)

    val uiState: StateFlow<RegisterUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        RegisterPresenter()
    }

    fun onEvent(event: RegisterAction) {
        _events.trySend(event)
    }

    @Composable
    private fun RegisterPresenter(): RegisterUiState {
        var state by remember { mutableStateOf(RegisterUiState()) }

        LaunchedEffect(Unit) {
            _events.receiveAsFlow().collect { action ->
                state = when (action) {
                    is RegisterAction.EmailChanged -> state.copy(email = action.email)
                    is RegisterAction.NameChanged -> state.copy(name = action.name)
                    is RegisterAction.PasswordChanged -> state.copy(password = action.password)
                    is RegisterAction.RegisterClicked -> {
                        resultFlow(
                            mapError = ::formatError
                        ) {
                                authRepository.register(
                                    email = state.email,
                                    name = state.name,
                                    password = state.password
                                )
                        }.collect { result ->
                            state = when (result) {
                                Result.Loading -> state.copy(isLoading = true, error = null)
                                is Result.Success -> state.copy(
                                    isLoading = false,
                                    error = null,
                                    registerSuccess = true
                                )
                                is Result.Error -> state.copy(
                                    isLoading = false,
                                    error = result.error
                                )
                            }
                        }
                        state
                    }
                    is RegisterAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: AuthError): AuthUiError =
        error.toAuthUiError(includeInvalidDetail = true)
}
