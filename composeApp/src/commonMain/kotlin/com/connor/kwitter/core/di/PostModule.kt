package com.connor.kwitter.core.di

import com.connor.kwitter.data.post.datasource.PostRemoteDataSource
import com.connor.kwitter.data.post.repository.PostRepositoryImpl
import com.connor.kwitter.domain.post.repository.PostRepository
import org.koin.dsl.module

private const val POST_BASE_URL = "http://192.168.0.101:8080"

val postModule = module {
    single {
        PostRemoteDataSource(
            httpClient = get(),
            baseUrl = POST_BASE_URL
        )
    }

    single<PostRepository> {
        PostRepositoryImpl(
            remoteDataSource = get(),
            tokenDataSource = get()
        )
    }
}
