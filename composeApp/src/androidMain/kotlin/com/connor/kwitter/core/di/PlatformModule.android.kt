package com.connor.kwitter.core.di

import com.connor.kwitter.data.datastore.createDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android 平台模块
 * 提供 Android 特定的依赖：
 * - DataStore: 使用 androidContext() 创建
 */
actual val platformModule = module {
    // DataStore - 使用 Android Context 创建
    single { createDataStore(androidContext()) }
}
