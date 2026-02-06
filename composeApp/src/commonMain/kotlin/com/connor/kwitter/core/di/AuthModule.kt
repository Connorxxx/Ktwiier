package com.connor.kwitter.core.di

import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.auth.repository.AuthRepositoryImpl
import com.connor.kwitter.domain.auth.repository.AuthRepository
import org.koin.dsl.module

private const val AUTH_BASE_URL = "http://192.168.0.101:8080"

/**
 * 认证模块
 * 提供认证相关的依赖注入
 */
val authModule = module {
    // TokenDataSource - 单例
    single {
        TokenDataSource(dataStore = get())
    }

    // AuthRemoteDataSource - 单例
    single {
        AuthRemoteDataSource(
            httpClient = get(),
            baseUrl = AUTH_BASE_URL
        )
    }

    // AuthRepository - 单例，绑定到接口
    single<AuthRepository> {
        AuthRepositoryImpl(
            remoteDataSource = get(),
            tokenDataSource = get()
        )
    }
}
