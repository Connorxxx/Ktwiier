package com.connor.kwitter.features.main

import com.connor.kwitter.features.NavigationRoute

enum class MainBottomTab(val label: String) {
    Home("首页"),
    Messages("私信"),
    Search("搜索"),
    Settings("设置")
}

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
 * 导航栈 + 当前选中 tab（两层分离）
 */
data class MainState(
    val isLoading: Boolean = false,
    // 当前选中的 tab（独立于 backStack）
    val selectedTab: MainBottomTab = MainBottomTab.Home,
    // 导航栈：认证后始终以 Home 为底，detail 页面 push 在上面
    val backStack: List<NavigationRoute>,
    // 行为：暴露给 UI 的回调
    val onNavigate: (NavigationRoute) -> Unit,
    // 替换式导航（Login ↔ Register）
    val onNavigateReplace: (NavigationRoute) -> Unit,
    // Tab 切换：设置 selectedTab + 清除 detail 页面
    val onNavigateRoot: (NavigationRoute) -> Unit,
    val onBack: () -> Unit
)

/**
 * Main 界面的 Action
 */
sealed interface MainAction {
    data object Load : MainAction
}
