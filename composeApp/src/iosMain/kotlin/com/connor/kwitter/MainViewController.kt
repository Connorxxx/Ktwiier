package com.connor.kwitter

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.di.initKoin
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
