package com.connor.kwitter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.core.crash.setupCrashReporting
import com.connor.kwitter.di.initKoin
import com.connor.kwitter.features.glass.NativeTabBridge
import com.connor.kwitter.features.glass.NativeTopBarBridge
import platform.UIKit.UITabBar
import platform.UIKit.UIView
import platform.UIKit.UIViewController

private val isDarkTheme = mutableStateOf(false)

fun setupPlatformCrashReporting() {
    setupCrashReporting()
}

fun MainViewController(isDarkTheme: Boolean): UIViewController {
    initKoin()
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
    NativeTopBarBridge.setDarkTheme(isDarkTheme)
    return ComposeUIViewController {
        App(isDarkTheme = com.connor.kwitter.isDarkTheme.value)
    }
}

fun updateDarkTheme(viewController: UIViewController, isDarkTheme: Boolean) {
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
    NativeTopBarBridge.setDarkTheme(isDarkTheme)
}

// Called from Swift after native UITabBar is configured
fun registerNativeTabBar(tabBar: UITabBar) {
    NativeTabBridge.configure(tabBar)
}

fun createNativeTopBarView(): UIView = NativeTopBarBridge.createTopBarView()

// Called from Swift delegate when a native tab is selected
fun onNativeTabSelected(index: Int) {
    NativeTabBridge.onNativeTabSelected(index)
}
