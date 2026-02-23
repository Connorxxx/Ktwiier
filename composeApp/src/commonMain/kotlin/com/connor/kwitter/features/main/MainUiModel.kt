package com.connor.kwitter.features.main

import com.connor.kwitter.features.NavigationRoute

enum class MainBottomTab {
    Home,
    Messages,
    Search,
    Settings
}

val mainBottomTabRoutes: List<NavigationRoute> = listOf(
    NavigationRoute.Home,
    NavigationRoute.ConversationList,
    NavigationRoute.Search,
    NavigationRoute.Settings
)

fun NavigationRoute.toBottomTabOrNull(): MainBottomTab? = when (this) {
    NavigationRoute.Home -> MainBottomTab.Home
    NavigationRoute.ConversationList -> MainBottomTab.Messages
    NavigationRoute.Search -> MainBottomTab.Search
    NavigationRoute.Settings -> MainBottomTab.Settings
    else -> null
}

fun MainBottomTab.toRoute(): NavigationRoute = when (this) {
    MainBottomTab.Home -> NavigationRoute.Home
    MainBottomTab.Messages -> NavigationRoute.ConversationList
    MainBottomTab.Search -> NavigationRoute.Search
    MainBottomTab.Settings -> NavigationRoute.Settings
}

/**
 * Main 界面的状态
 * 导航栈本身就是数据
 */
data class MainState(
    val isLoading: Boolean = false,
    // 导航栈：认证后包含所有 tab 根路由，detail 页面压在顶部
    val backStack: List<NavigationRoute>,
    // 行为：暴露给 UI 的回调，用于修改状态
    val onNavigate: (NavigationRoute) -> Unit,
    // 替换式导航：移除栈中所有相同类型路由，然后添加新路由（实现 singleTop）
    val onNavigateReplace: (NavigationRoute) -> Unit,
    // 顶层导航：切换 tab 时把目标 tab 移到栈顶（保留所有 tab 根路由）
    val onNavigateRoot: (NavigationRoute) -> Unit,
    val onBack: () -> Unit
)

/**
 * Main 界面的 Action
 */
sealed interface MainAction {
    data object Load : MainAction
}
