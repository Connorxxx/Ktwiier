package com.connor.kwitter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.di.initKoin
import com.connor.kwitter.features.glass.NativeTabBridge
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController

private val isDarkTheme = mutableStateOf(false)

fun MainViewController(isDarkTheme: Boolean): UIViewController {
    initKoin()
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
    return ComposeUIViewController {
        App(isDarkTheme = com.connor.kwitter.isDarkTheme.value)
    }
}

fun updateDarkTheme(viewController: UIViewController, isDarkTheme: Boolean) {
    com.connor.kwitter.isDarkTheme.value = isDarkTheme
}

// Called from Swift after UITabBarController is configured
fun registerTabBarController(controller: UITabBarController) {
    NativeTabBridge.configure(controller)
}

// Called from Swift delegate when a native tab is selected
fun onNativeTabSelected(index: Int) {
    NativeTabBridge.onNativeTabSelected(index)
}
