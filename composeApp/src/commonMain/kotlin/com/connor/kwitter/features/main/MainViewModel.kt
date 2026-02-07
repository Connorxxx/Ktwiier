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
import com.connor.kwitter.domain.auth.model.UserSession
import com.connor.kwitter.domain.auth.repository.AuthRepository
import com.connor.kwitter.features.NavigationRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

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

    private val events = Channel<MainAction>(Channel.UNLIMITED)

    val state: StateFlow<MainState> = viewModelScope.launchMolecule(
        mode = RecompositionMode.Immediate
    ) {
        MainPresenter()
    }

    fun onAction(action: MainAction) {
        events.trySend(action)
    }

    @Composable
    private fun MainPresenter(): MainState {
        var isLoading by remember { mutableStateOf(false) }

        // 导航栈状态
        val backStack = remember { mutableStateListOf<NavigationRoute>() }

        val onNavigate: (NavigationRoute) -> Unit = remember {
            { route -> backStack.add(route) }
        }

        // 替换式导航：移除栈中所有指定类型，然后添加新路由（实现 singleTop 效果）
        val onNavigateReplace: (NavigationRoute) -> Unit = remember {
            { route ->
                // 移除栈中所有相同类型的路由
                backStack.removeAll { it::class == route::class }
                backStack.add(route)
            }
        }

        val onBack: () -> Unit = remember {
            { backStack.removeLastOrNull() }
        }

        val session by authRepository.session.collectAsState(initial = null)

        LaunchedEffect(session) {
            val route = when (session) {
                is UserSession.Authenticated -> NavigationRoute.Home
                UserSession.Unauthenticated -> NavigationRoute.Login
                null -> NavigationRoute.Splash
            }
            backStack.clear()
            backStack.add(route)
        }

        // 处理 Action
        LaunchedEffect(Unit) {
            events.receiveAsFlow().collect { action ->
                when (action) {
                    is MainAction.Load -> {
                        isLoading = true
                        // 可以在这里做初始化工作
                        isLoading = false
                    }
                }
            }
        }

        return MainState(
            isLoading = isLoading,
            backStack = backStack,
            onNavigate = onNavigate,
            onNavigateReplace = onNavigateReplace,
            onBack = onBack
        )
    }
}
