package com.connor.kwitter.core.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * 网络模块
 * 提供 HttpClient 配置
 */
val networkModule = module {
    single {
        HttpClient {
            // JSON 内容协商
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            // 日志记录
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }

            // 可以在这里添加更多配置，如：
            // - 请求超时
            // - 默认请求头
            // - 重试策略等
        }
    }
}
