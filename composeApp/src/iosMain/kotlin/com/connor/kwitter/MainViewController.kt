package com.connor.kwitter

import androidx.compose.ui.window.ComposeUIViewController
import com.connor.kwitter.di.initKoin

/**
 * iOS 主视图控制器
 * 在创建 Compose UI 之前初始化 Koin
 */
fun MainViewController() = ComposeUIViewController {
    // 初始化 Koin（只会初始化一次）
    initKoin()

    App()
}