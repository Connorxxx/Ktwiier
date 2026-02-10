package com.connor.kwitter.core.di

import com.connor.kwitter.data.datastore.createDataStore
import com.connor.kwitter.data.post.local.createDatabaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { createDataStore(androidContext()) }
    single { createDatabaseBuilder(androidContext()) }
}
