package com.connor.kwitter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.connor.kwitter.features.main.MainScreen

/**
 * 带认证功能的应用入口
 * 使用 Navigation 3 管理导航栈
 */
@Composable
fun AppWithAuth() {
    MaterialTheme {
        MainScreen()
    }
}
