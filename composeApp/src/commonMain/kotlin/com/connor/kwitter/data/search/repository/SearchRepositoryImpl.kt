package com.connor.kwitter.data.search.repository

import arrow.core.Either
import com.connor.kwitter.data.search.datasource.SearchRemoteDataSource
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.search.model.SearchError
import com.connor.kwitter.domain.search.repository.SearchRepository
import com.connor.kwitter.domain.user.model.UserList

class SearchRepositoryImpl(
    private val remoteDataSource: SearchRemoteDataSource
) : SearchRepository {

    override suspend fun searchPosts(
        query: String,
        sort: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, PostList> {
        return remoteDataSource.searchPosts(query, sort, limit, offset)
    }

    override suspend fun searchReplies(
        query: String,
        sort: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, PostList> {
        return remoteDataSource.searchReplies(query, sort, limit, offset)
    }

    override suspend fun searchUsers(
        query: String,
        limit: Int,
        offset: Int
    ): Either<SearchError, UserList> {
        return remoteDataSource.searchUsers(query, limit, offset)
    }
}
