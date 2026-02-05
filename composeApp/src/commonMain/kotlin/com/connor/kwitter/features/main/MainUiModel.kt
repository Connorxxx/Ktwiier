package com.connor.kwitter.features.main

import com.connor.kwitter.features.NavigationRoute

/**
 * Main 界面的状态
 * 核心：导航栈本身就是数据
 */
data class MainState(
    val isLoading: Boolean = false,
    // 导航栈作为数据
    val backStack: List<NavigationRoute>,
    // 行为：暴露给 UI 的回调，用于修改状态
    val onNavigate: (NavigationRoute) -> Unit,
    val onBack: () -> Unit
)

/**
 * Main 界面的 Action
 */
sealed interface MainAction {
    data object Load : MainAction
}
