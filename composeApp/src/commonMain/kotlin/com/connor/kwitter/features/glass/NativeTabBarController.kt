package com.connor.kwitter.features.glass

import kotlinx.coroutines.flow.Flow

interface NativeTabBarController {
    val tabSelectionEvents: Flow<Int>
    val tabBarHeightFlow: Flow<Double>
    fun syncSelectedIndex(index: Int)
    fun setTabBarVisible(visible: Boolean)
}

expect fun getNativeTabBarController(): NativeTabBarController?
