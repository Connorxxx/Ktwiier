package com.connor.kwitter.features.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.features.NavigationRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main ViewModel
 * 使用 Molecule 进行状态管理
 *
 * 职责：
 * - 管理导航栈
 * - 根据认证状态决定初始路由
 */
class MainViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val events = MutableSharedFlow<MainAction>(extraBufferCapacity = 10)

    val state: StateFlow<MainState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        MainPresenter()
    }

    fun onAction(action: MainAction) {
        events.tryEmit(action)
    }

    @Composable
    private fun MainPresenter(): MainState {
        var isLoading by remember { mutableStateOf(false) }

        // 导航栈状态
        val backStack = remember { mutableStateListOf<NavigationRoute>() }

        val onNavigate: (NavigationRoute) -> Unit = remember {
            { route -> backStack.add(route) }
        }

        val onBack: () -> Unit = remember {
            { backStack.removeLastOrNull() }
        }

        // 监听认证 Token 状态，决定初始路由
        val token by authRepository.token.collectAsState(initial = null)

        LaunchedEffect(token) {
            val route = when {
                token != null -> NavigationRoute.Home
                else -> NavigationRoute.Register // 暂时默认到注册页
            }
            backStack.clear()
            backStack.add(route)
        }

        // 处理 Action
        val eventFlow by events.collectAsState(null)
        LaunchedEffect(eventFlow) {
            when (eventFlow) {
                is MainAction.Load -> {
                    isLoading = true
                    // 可以在这里做初始化工作
                    isLoading = false
                }
                null -> { /* 无事件 */ }
            }
        }

        return MainState(
            isLoading = isLoading,
            backStack = backStack,
            onNavigate = onNavigate,
            onBack = onBack
        )
    }
}
