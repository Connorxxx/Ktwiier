package com.connor.kwitter.di

import com.connor.kwitter.core.di.authModule
import com.connor.kwitter.core.di.networkModule
import com.connor.kwitter.core.di.platformModule
import com.connor.kwitter.core.di.viewModelModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            platformModule,
            networkModule,
            authModule,
            viewModelModule
        )
    }
}
