package com.connor.kwitter.di

import com.connor.kwitter.core.di.authModule
import com.connor.kwitter.core.di.networkModule
import com.connor.kwitter.core.di.platformModule
import com.connor.kwitter.core.di.viewModelModule
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

/**
 * iOS 平台的 Koin 初始化
 * 在 iOS 的 MainViewController 中调用
 *
 * 使用 GlobalContext 检查确保只初始化一次
 */
fun initKoin() {
    // 检查 Koin 是否已经初始化
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            modules(
                platformModule,  // 平台特定模块（DataStore）
                networkModule,   // 网络模块（HttpClient）
                authModule,      // 认证模块（Repository, DataSource）
                viewModelModule  // ViewModel 模块
            )
        }
    }
}
