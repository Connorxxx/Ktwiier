package com.connor.kwitter.di

import com.connor.kwitter.core.di.authModule
import com.connor.kwitter.core.di.mediaModule
import com.connor.kwitter.core.di.networkModule
import com.connor.kwitter.core.di.notificationModule
import com.connor.kwitter.core.di.platformModule
import com.connor.kwitter.core.di.postModule
import com.connor.kwitter.core.di.userModule
import com.connor.kwitter.core.di.viewModelModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            platformModule,
            networkModule,
            notificationModule,
            authModule,
            mediaModule,
            postModule,
            userModule,
            viewModelModule
        )
    }
}
