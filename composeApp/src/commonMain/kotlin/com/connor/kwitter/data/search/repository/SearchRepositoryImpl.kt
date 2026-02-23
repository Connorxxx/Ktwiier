package com.connor.kwitter.data.search.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.connor.kwitter.core.paging.OffsetPagingSource
import com.connor.kwitter.data.search.datasource.SearchRemoteDataSource
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.search.repository.SearchRepository
import com.connor.kwitter.domain.user.model.UserListItem
import kotlinx.coroutines.flow.Flow

class SearchRepositoryImpl(
    private val remoteDataSource: SearchRemoteDataSource
) : SearchRepository {

    private companion object {
        const val PAGE_SIZE = 20
    }

    override fun searchPostsPaging(query: String, sort: String): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.searchPosts(query, sort, limit, offset)
                .map { it.posts to it.hasMore }
        }
    }.flow

    override fun searchRepliesPaging(query: String, sort: String): Flow<PagingData<Post>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.searchReplies(query, sort, limit, offset)
                .map { it.posts to it.hasMore }
        }
    }.flow

    override fun searchUsersPaging(query: String): Flow<PagingData<UserListItem>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
    ) {
        OffsetPagingSource { limit, offset ->
            remoteDataSource.searchUsers(query, limit, offset)
                .map { it.users to it.hasMore }
        }
    }.flow
}
