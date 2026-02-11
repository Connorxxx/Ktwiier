package com.connor.kwitter.core.di

import com.connor.kwitter.data.auth.datasource.AuthEventSource
import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.auth.repository.AuthRepositoryImpl
import com.connor.kwitter.domain.auth.repository.AuthRepository
import org.koin.dsl.module

val authModule = module {
    single {
        TokenDataSource(dataStore = get())
    }

    single {
        AuthRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single {
        AuthEventSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<AuthRepository> {
        AuthRepositoryImpl(
            remoteDataSource = get(),
            tokenDataSource = get(),
            authEventSource = get()
        )
    }
}
