package com.connor.kwitter

import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}