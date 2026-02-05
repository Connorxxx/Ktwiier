package com.connor.kwitter.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import arrow.core.Either
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

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
    data object LoginClick : RegisterAction
}

/**
 * 注册导航事件
 */
sealed interface RegisterNavAction : RegisterIntent {
    data object OnRegisterSuccess : RegisterNavAction
}

/**
 * 注册 ViewModel
 * 使用 Molecule 进行状态管理
 */
class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val events = MutableSharedFlow<RegisterAction>(extraBufferCapacity = 10)

    val uiState: StateFlow<RegisterUiState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        RegisterPresenter()
    }

    fun onEvent(event: RegisterAction) {
        events.tryEmit(event)
    }

    @Composable
    private fun RegisterPresenter(): RegisterUiState {
        var state by remember { mutableStateOf(RegisterUiState()) }
        val eventFlow by events.collectAsState(null)

        LaunchedEffect(eventFlow) {
            when (val event = eventFlow) {
                is RegisterAction.EmailChanged -> {
                    state = state.copy(email = event.email)
                }
                is RegisterAction.NameChanged -> {
                    state = state.copy(name = event.name)
                }
                is RegisterAction.PasswordChanged -> {
                    state = state.copy(password = event.password)
                }
                is RegisterAction.RegisterClicked -> {
                    state = state.copy(isLoading = true, error = null)

                    when (val result = authRepository.register(
                        email = state.email,
                        name = state.name,
                        password = state.password
                    )) {
                        is Either.Left -> {
                            state = state.copy(
                                isLoading = false,
                                error = formatError(result.value)
                            )
                        }
                        is Either.Right -> {
                            state = state.copy(
                                isLoading = false,
                                registeredToken = result.value
                            )
                        }
                    }
                }
                is RegisterAction.ErrorDismissed -> {
                    state = state.copy(error = null)
                }
                is RegisterAction.LoginClick -> {
                    // 导航由外部处理
                }
                null -> { /* 无事件 */ }
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
