package com.connor.kwitter.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * DataStore 文件名常量
 */
const val DATASTORE_FILE_NAME = "kwitter_preferences.preferences_pb"

/**
 * 创建 DataStore 实例的 expect 函数
 *
 * 为什么需要 expect/actual？
 * - DataStore API 本身是跨平台的
 * - 但文件系统访问是平台特定的：
 *   - Android: 需要 Context 获取 filesDir
 *   - iOS: 需要 NSFileManager 获取文档目录
 *
 * @param context Android 平台需要，iOS 平台忽略（通过重载实现）
 */
expect fun createDataStore(context: Any? = null): DataStore<Preferences>
