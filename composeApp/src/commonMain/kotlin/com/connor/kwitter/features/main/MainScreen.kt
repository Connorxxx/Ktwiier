package com.connor.kwitter.features.main

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.connor.kwitter.features.NavigationRoute
import com.connor.kwitter.features.login.LoginScreen
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
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = mainState.backStack,
        onBack = mainState.onBack,

        // 🎨 全局前进动画：缩放 + 淡入淡出 + 水平滑动
        transitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 3 }, // 从右侧 1/3 位置开始
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.95f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec = tween(400)
                )
            )
        },

        // 🔙 全局后退动画：反向缩放 + 淡入淡出
        popTransitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.95f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec = tween(400)
                )
            )
        },

        // 📱 预测性返回手势（与 popTransitionSpec 保持一致）
        predictivePopTransitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(400)
                )
            )
        },

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
                                // Login/Register 互相导航使用 replace，避免栈累积
                                RegisterNavAction.LoginClick -> mainState.onNavigateReplace(NavigationRoute.Login)
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Login> {
                LoginScreen(
                    onLoginSuccess = { mainState.onNavigate(NavigationRoute.Home) },
                    // Login/Register 互相导航使用 replace，避免栈累积
                    onRegisterClick = { mainState.onNavigateReplace(NavigationRoute.Register) }
                )
            }

            entry<NavigationRoute.Home> {
                HomeScreen(
                    onLogout = { mainState.onNavigate(NavigationRoute.Login) }
                )
            }

            entry<NavigationRoute.Splash> {
                SplashScreen()
            }
        }
    )
}
