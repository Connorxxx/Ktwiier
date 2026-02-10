package com.connor.kwitter.core.di

import com.connor.kwitter.data.user.datasource.UserRemoteDataSource
import com.connor.kwitter.data.user.repository.UserRepositoryImpl
import com.connor.kwitter.domain.user.repository.UserRepository
import org.koin.dsl.module

private const val USER_BASE_URL = "http://192.168.0.101:8080"

val userModule = module {
    single {
        UserRemoteDataSource(
            httpClient = get(),
            baseUrl = USER_BASE_URL
        )
    }

    single<UserRepository> {
        UserRepositoryImpl(
            remoteDataSource = get(),
            tokenDataSource = get()
        )
    }
}
