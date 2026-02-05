package com.connor.kwitter.core.di

import org.koin.core.module.Module

/**
 * 平台特定模块
 * 提供平台相关的依赖注入（如 DataStore、Context 等）
 *
 * 为什么需要 expect/actual？
 * - Android: 需要 Context 来创建 DataStore
 * - iOS: 使用平台特定的文件路径
 * - Desktop/Web: 各有不同的存储方案
 */
expect val platformModule: Module
