package com.connor.kwitter.core.di

import com.connor.kwitter.features.auth.RegisterViewModel
import com.connor.kwitter.features.createpost.CreatePostViewModel
import com.connor.kwitter.features.home.HomeViewModel
import com.connor.kwitter.features.login.LoginViewModel
import com.connor.kwitter.features.main.MainViewModel
import com.connor.kwitter.features.postdetail.PostDetailViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * ViewModel 模块
 * 提供所有 ViewModel 的依赖注入
 */
val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PostDetailViewModel)
    viewModelOf(::CreatePostViewModel)
}
