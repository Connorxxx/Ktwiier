package com.connor.kwitter

import android.app.Application
import com.connor.kwitter.core.di.authModule
import com.connor.kwitter.core.di.networkModule
import com.connor.kwitter.core.di.platformModule
import com.connor.kwitter.core.di.postModule
import com.connor.kwitter.core.di.userModule
import com.connor.kwitter.core.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KwitterApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            // 启用 Android 日志
            androidLogger()

            // 注入 Android Context
            androidContext(this@KwitterApplication)

            // 加载模块
            modules(
                platformModule,  // 平台特定模块（DataStore, Database）
                networkModule,   // 网络模块（HttpClient）
                authModule,      // 认证模块（Repository, DataSource）
                postModule,      // 帖子模块（Repository, DataSource）
                userModule,      // 用户模块（Repository, DataSource）
                viewModelModule  // ViewModel 模块
            )
        }
    }
}
