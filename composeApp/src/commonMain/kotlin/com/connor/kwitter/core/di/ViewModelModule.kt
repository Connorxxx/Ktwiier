package com.connor.kwitter.core.di

import com.connor.kwitter.features.auth.RegisterViewModel
import com.connor.kwitter.features.chat.ChatViewModel
import com.connor.kwitter.features.conversationlist.ConversationListViewModel
import com.connor.kwitter.features.createpost.CreatePostViewModel
import com.connor.kwitter.features.editprofile.EditProfileViewModel
import com.connor.kwitter.features.home.HomeViewModel
import com.connor.kwitter.features.login.LoginViewModel
import com.connor.kwitter.features.main.MainViewModel
import com.connor.kwitter.features.mediaviewer.MediaViewerViewModel
import com.connor.kwitter.features.messagesearch.MessageSearchViewModel
import com.connor.kwitter.features.postdetail.PostDetailViewModel
import com.connor.kwitter.features.userprofile.UserProfileViewModel
import com.connor.kwitter.features.userlist.UserListViewModel
import com.connor.kwitter.features.search.SearchViewModel
import com.connor.kwitter.features.settings.SettingsViewModel
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
    viewModelOf(::MediaViewerViewModel)
    viewModelOf(::UserProfileViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::UserListViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ConversationListViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::MessageSearchViewModel)
    viewModelOf(::SettingsViewModel)
}
