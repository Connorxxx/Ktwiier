package com.connor.kwitter.domain.search.repository

import arrow.core.Either
import com.connor.kwitter.domain.post.model.PostList
import com.connor.kwitter.domain.search.model.SearchError
import com.connor.kwitter.domain.user.model.UserList

interface SearchRepository {
    suspend fun searchPosts(
        query: String,
        sort: String = "best_match",
        limit: Int = 20,
        offset: Int = 0
    ): Either<SearchError, PostList>

    suspend fun searchReplies(
        query: String,
        sort: String = "best_match",
        limit: Int = 20,
        offset: Int = 0
    ): Either<SearchError, PostList>

    suspend fun searchUsers(
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): Either<SearchError, UserList>
}
