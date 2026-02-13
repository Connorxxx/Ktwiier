@file:OptIn(ExperimentalForeignApi::class)

package com.connor.kwitter.features.glass

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarItem
import platform.UIKit.UIView

object NativeTabBridge : NativeTabBarController {
    private val _tabSelectionEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val tabSelectionEvents: Flow<Int> = _tabSelectionEvents

    private var tabBar: UITabBar? = null
    private var desiredSelectedIndex: Int = 0
    private var desiredVisible: Boolean = true

    fun configure(tabBar: UITabBar) {
        this.tabBar = tabBar
        applySelectedIndex(tabBar, desiredSelectedIndex)
        tabBar.alpha = if (desiredVisible) 1.0 else 0.0
        tabBar.userInteractionEnabled = desiredVisible
    }

    fun onNativeTabSelected(index: Int) {
        _tabSelectionEvents.tryEmit(index)
    }

    override fun syncSelectedIndex(index: Int) {
        desiredSelectedIndex = index
        val nativeTabBar = tabBar ?: return
        applySelectedIndex(nativeTabBar, index)
    }

    private fun applySelectedIndex(nativeTabBar: UITabBar, index: Int) {
        val items = nativeTabBar.items ?: return
        val selectedItem = (items.getOrNull(index) as? UITabBarItem) ?: return
        if (nativeTabBar.selectedItem !== selectedItem) {
            nativeTabBar.selectedItem = selectedItem
        }
    }

    override fun setTabBarVisible(visible: Boolean) {
        desiredVisible = visible
        val tabBar = tabBar ?: return
        UIView.animateWithDuration(
            duration = 0.35,
            animations = {
                tabBar.alpha = if (visible) 1.0 else 0.0
            }
        )
        tabBar.userInteractionEnabled = visible
    }
}
