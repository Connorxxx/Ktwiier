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
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 登录界面的状态
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedInToken: AuthToken? = null
)

/**
 * 登录界面的 Intent 基础接口
 */
sealed interface LoginIntent

/**
 * 登录界面的事件 - UI 交互
 */
sealed interface LoginAction : LoginIntent {
    data class EmailChanged(val email: String) : LoginAction
    data class PasswordChanged(val password: String) : LoginAction
    data object LoginClicked : LoginAction
    data object ErrorDismissed : LoginAction
}

/**
 * 登录导航事件
 */
sealed interface LoginNavAction : LoginIntent {
    data object OnLoginSuccess : LoginNavAction
    data object RegisterClick : LoginNavAction
}

/**
 * 登录 ViewModel
 * 使用 Molecule 进行状态管理
 */
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
                            ifRight = { token ->
                                loading.copy(isLoading = false, loggedInToken = token)
                            }
                        )
                    }
                    is LoginAction.ErrorDismissed -> state.copy(error = null)
                }
            }
        }

        return state
    }

    private fun formatError(error: AuthError): String = when (error) {
        is AuthError.NetworkError -> "网络错误: ${error.message}"
        is AuthError.ServerError -> "服务器错误 (${error.code}): ${error.message}"
        is AuthError.ClientError -> "请求错误 (${error.code}): ${error.message}"
        is AuthError.InvalidCredentials -> "邮箱或密码错误"
        is AuthError.StorageError -> "存储错误: ${error.message}"
        is AuthError.Unknown -> "未知错误: ${error.message}"
    }
}
