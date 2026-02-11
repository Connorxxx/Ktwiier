package com.connor.kwitter.core.di

import com.connor.kwitter.data.user.datasource.UserRemoteDataSource
import com.connor.kwitter.data.user.repository.UserRepositoryImpl
import com.connor.kwitter.domain.user.repository.UserRepository
import org.koin.dsl.module

val userModule = module {
    single {
        UserRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<UserRepository> {
        UserRepositoryImpl(
            remoteDataSource = get()
        )
    }
}
