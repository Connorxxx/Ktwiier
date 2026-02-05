package com.connor.kwitter.core.di

import com.connor.kwitter.data.datastore.createDataStore
import org.koin.dsl.module

/**
 * iOS 平台模块
 * 提供 iOS 特定的依赖：
 * - DataStore: 使用 NSFileManager 创建
 */
actual val platformModule = module {
    // DataStore - 使用 iOS 文件系统创建
    single { createDataStore() }
}
