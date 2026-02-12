package com.connor.kwitter.domain.search.repository

import androidx.paging.PagingData
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.user.model.UserListItem
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun searchPostsPaging(query: String, sort: String): Flow<PagingData<Post>>
    fun searchRepliesPaging(query: String, sort: String): Flow<PagingData<Post>>
    fun searchUsersPaging(query: String): Flow<PagingData<UserListItem>>
}
