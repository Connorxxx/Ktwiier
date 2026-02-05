package com.connor.kwitter.features

import kotlinx.serialization.Serializable

/**
 * 导航路由定义
 * 使用 sealed interface + @Serializable 实现类型安全的导航
 */
@Serializable
sealed interface NavigationRoute {
    @Serializable
    data object Splash : NavigationRoute

    @Serializable
    data object Login : NavigationRoute

    @Serializable
    data object Register : NavigationRoute

    @Serializable
    data object Home : NavigationRoute
}
