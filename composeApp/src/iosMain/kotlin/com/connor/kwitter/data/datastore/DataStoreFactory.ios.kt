package com.connor.kwitter.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS 平台的 DataStore 实现
 * 使用 NSFileManager 获取文档目录
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createDataStore(context: Any?): DataStore<Preferences> {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val dataStoreFile = requireNotNull(documentDirectory?.path) + "/$DATASTORE_FILE_NAME"

    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStoreFile.toPath() }
    )
}
