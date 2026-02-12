package com.connor.kwitter.core.di

import com.connor.kwitter.data.notification.NotificationRepositoryImpl
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.notification.repository.NotificationRepository
import org.koin.dsl.module

val notificationModule = module {
    single {
        NotificationService(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<NotificationRepository> {
        NotificationRepositoryImpl(
            notificationService = get()
        )
    }
}
