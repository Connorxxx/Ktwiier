package com.connor.kwitter.core.di

import com.connor.kwitter.data.messaging.datasource.MessagingRemoteDataSource
import com.connor.kwitter.data.messaging.repository.MessagingRepositoryImpl
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.domain.messaging.repository.MessagingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val messagingModule = module {
    single {
        MessagingRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<MessagingRepository> {
        val database: AppDatabase = get()
        MessagingRepositoryImpl(
            remoteDataSource = get(),
            notificationService = get(),
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }
}
