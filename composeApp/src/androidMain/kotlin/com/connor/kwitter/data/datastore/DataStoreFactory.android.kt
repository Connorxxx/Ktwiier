package com.connor.kwitter.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * Android 平台的 DataStore 实现
 * 使用 Context 获取应用数据目录
 */
actual fun createDataStore(context: Any?): DataStore<Preferences> {
    val androidContext = context as? Context
        ?: error("Android DataStore requires a non-null Context")
    val dataStoreFile = androidContext.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath

    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStoreFile.toPath() }
    )
}
