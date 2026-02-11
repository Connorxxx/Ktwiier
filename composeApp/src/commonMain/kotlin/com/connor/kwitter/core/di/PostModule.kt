package com.connor.kwitter.core.di

import com.connor.kwitter.data.post.datasource.PostRemoteDataSource
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.data.post.local.createDatabase
import com.connor.kwitter.data.post.repository.PostRepositoryImpl
import com.connor.kwitter.domain.post.repository.PostRepository
import org.koin.dsl.module

val postModule = module {
    single {
        PostRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<AppDatabase> { createDatabase(get()) }

    single<PostRepository> {
        PostRepositoryImpl(
            remoteDataSource = get(),
            database = get()
        )
    }
}
