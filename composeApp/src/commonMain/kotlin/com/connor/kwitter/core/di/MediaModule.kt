package com.connor.kwitter.core.di

import com.connor.kwitter.data.media.datasource.MediaRemoteDataSource
import com.connor.kwitter.data.media.repository.MediaRepositoryImpl
import com.connor.kwitter.domain.media.repository.MediaRepository
import org.koin.dsl.module

val mediaModule = module {
    single {
        MediaRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<MediaRepository> {
        MediaRepositoryImpl(
            remoteDataSource = get()
        )
    }
}
