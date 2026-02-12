package com.connor.kwitter.core.di

import com.connor.kwitter.data.messaging.datasource.MessagingRemoteDataSource
import com.connor.kwitter.data.messaging.repository.MessagingRepositoryImpl
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import org.koin.dsl.module

val messagingModule = module {
    single {
        MessagingRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<MessagingRepository> {
        MessagingRepositoryImpl(
            remoteDataSource = get(),
            notificationService = get()
        )
    }
}
