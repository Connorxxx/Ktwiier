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
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 注册界面的状态
 */
data class RegisterUiState(
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val registeredToken: AuthToken? = null
)

/**
 * 注册界面的 Intent 基础接口
 */
sealed interface RegisterIntent

/**
 * 注册界面的事件 - UI 交互
 */
sealed interface RegisterAction : RegisterIntent {
    data class EmailChanged(val email: String) : RegisterAction
    data class NameChanged(val name: String) : RegisterAction
    data class PasswordChanged(val password: String) : RegisterAction
    data object RegisterClicked : RegisterAction
    data object ErrorDismissed : RegisterAction

}

/**
 * 注册导航事件
 */
sealed interface RegisterNavAction : RegisterIntent {
    data object OnRegisterSuccess : RegisterNavAction
    data object LoginClick : RegisterNavAction
}

/**
 * 注册 ViewModel
 * 使用 Molecule 进行状态管理
 */
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
                        val loading = state.copy(isLoading = true, error = null)
                        state = loading

                        val result = authRepository.register(
                            email = loading.email,
                            name = loading.name,
                            password = loading.password
                        )

                        result.fold(
                            ifLeft = { error ->
                                loading.copy(isLoading = false, error = formatError(error))
                            },
                            ifRight = { token ->
                                loading.copy(isLoading = false, registeredToken = token)
                            }
                        )
                    }
                    is RegisterAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: AuthError): String = when (error) {
        is AuthError.NetworkError -> "网络错误: ${error.message}"
        is AuthError.ServerError -> "服务器错误 (${error.code}): ${error.message}"
        is AuthError.ClientError -> "请求错误 (${error.code}): ${error.message}"
        is AuthError.InvalidCredentials -> "无效凭证: ${error.message}"
        is AuthError.StorageError -> "存储错误: ${error.message}"
        is AuthError.Unknown -> "未知错误: ${error.message}"
    }
}
