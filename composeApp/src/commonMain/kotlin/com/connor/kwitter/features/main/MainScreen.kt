package com.connor.kwitter.features.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.connor.kwitter.features.NavigationRoute
import com.connor.kwitter.features.auth.LoginScreen
import com.connor.kwitter.features.auth.RegisterAction
import com.connor.kwitter.features.auth.RegisterNavAction
import com.connor.kwitter.features.auth.RegisterScreen
import com.connor.kwitter.features.auth.RegisterViewModel
import com.connor.kwitter.features.home.HomeScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main Screen - 应用的主入口
 * 使用 Navigation 3 管理导航栈
 */
@Composable
fun MainScreen(
    mainVm: MainViewModel = koinViewModel()
) {
    val mainState by mainVm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainVm.onAction(MainAction.Load)
    }

    NavDisplay(
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = mainState.backStack,
        onBack = mainState.onBack,
        entryProvider = entryProvider {
            entry<NavigationRoute.Register> {
                val vm: RegisterViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                RegisterScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is RegisterAction -> vm.onEvent(action)
                            is RegisterNavAction -> when (action) {
                                RegisterNavAction.OnRegisterSuccess -> mainState.onNavigate(NavigationRoute.Home)
                                RegisterNavAction.OnLoginClick -> mainState.onNavigate(NavigationRoute.Login)
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Login> {
                LoginScreen(
                    onLoginSuccess = { mainState.onNavigate(NavigationRoute.Home) },
                    onRegisterClick = { mainState.onNavigate(NavigationRoute.Register) }
                )
            }

            entry<NavigationRoute.Home> {
                HomeScreen(
                    onLogout = { mainState.onNavigate(NavigationRoute.Login) }
                )
            }

            entry<NavigationRoute.Splash> {
                // 暂时不需要 Splash，直接路由到 Register/Home
            }
        }
    )
}
