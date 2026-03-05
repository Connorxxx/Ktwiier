package com.connor.kwitter

import android.app.Application
import com.connor.kwitter.core.crash.setupCrashReporting
import com.connor.kwitter.core.di.NETWORK_ENGINE_CIO
import com.connor.kwitter.core.di.authModule
import com.connor.kwitter.core.di.mediaModule
import com.connor.kwitter.core.di.messagingModule
import com.connor.kwitter.core.di.networkModule
import com.connor.kwitter.core.di.NETWORK_ENGINE_CRONET
import com.connor.kwitter.core.di.NETWORK_ENGINE_PROPERTY_KEY
import com.connor.kwitter.core.di.notificationModule
import com.connor.kwitter.core.di.platformModule
import com.connor.kwitter.core.di.postModule
import com.connor.kwitter.core.di.searchModule
import com.connor.kwitter.core.di.userModule
import com.connor.kwitter.core.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KwitterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashReporting()

        startKoin {
            // 启用 Android 日志
            androidLogger()

            // 注入 Android Context
            androidContext(this@KwitterApplication)

            // 网络引擎灰度开关: "cronet" / "cio"
            properties(
                mapOf(
                    NETWORK_ENGINE_PROPERTY_KEY to NETWORK_ENGINE_CRONET,
                ),
            )

            // 加载模块
            modules(
                platformModule,       // 平台特定模块（DataStore, Database）
                networkModule,        // 网络模块（HttpClient）
                notificationModule,   // 通知模块（WebSocket, NotificationRepository）
                authModule,           // 认证模块（Repository, DataSource）
                mediaModule,          // 媒体模块（MediaRepository）
                postModule,           // 帖子模块（Repository, DataSource）
                userModule,           // 用户模块（Repository, DataSource）
                searchModule,         // 搜索模块（Repository, DataSource）
                messagingModule,      // 私信模块（Repository, DataSource）
                viewModelModule       // ViewModel 模块
            )
        }
    }
}
