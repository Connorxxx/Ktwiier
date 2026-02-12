package com.connor.kwitter.core.di

import com.connor.kwitter.data.search.datasource.SearchRemoteDataSource
import com.connor.kwitter.data.search.repository.SearchRepositoryImpl
import com.connor.kwitter.domain.search.repository.SearchRepository
import org.koin.dsl.module

val searchModule = module {
    single {
        SearchRemoteDataSource(
            httpClient = get(),
            baseUrl = BASE_URL
        )
    }

    single<SearchRepository> {
        SearchRepositoryImpl(
            remoteDataSource = get()
        )
    }
}
