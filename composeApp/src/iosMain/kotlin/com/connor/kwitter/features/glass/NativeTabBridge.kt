@file:OptIn(ExperimentalForeignApi::class)

package com.connor.kwitter.features.glass

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.UIKit.UITabBarController
import platform.UIKit.UIView

object NativeTabBridge : NativeTabBarController {
    private val _tabSelectionEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    override val tabSelectionEvents: Flow<Int> = _tabSelectionEvents

    private var tabBarController: UITabBarController? = null

    fun configure(controller: UITabBarController) {
        tabBarController = controller
    }

    fun onNativeTabSelected(index: Int) {
        _tabSelectionEvents.tryEmit(index)
    }

    override fun syncSelectedIndex(index: Int) {
        val controller = tabBarController ?: return
        if (controller.selectedIndex.toInt() != index) {
            controller.selectedIndex = index.toULong()
        }
    }

    override fun setTabBarVisible(visible: Boolean) {
        val tabBar = tabBarController?.tabBar ?: return
        UIView.animateWithDuration(
            duration = 0.35,
            animations = {
                tabBar.alpha = if (visible) 1.0 else 0.0
            }
        )
        tabBar.userInteractionEnabled = visible
    }
}
