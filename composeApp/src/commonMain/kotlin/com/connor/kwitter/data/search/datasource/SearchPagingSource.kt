package com.connor.kwitter.data.search.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import arrow.core.Either
import com.connor.kwitter.domain.search.model.SearchError

internal class SearchPagingSource<V : Any>(
    private val loader: suspend (limit: Int, offset: Int) -> Either<SearchError, Pair<List<V>, Boolean>>
) : PagingSource<Int, V>() {

    override fun getRefreshKey(state: PagingState<Int, V>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(page.data.size)
                    ?: page.nextKey?.minus(page.data.size)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, V> {
        val offset = params.key ?: 0
        return loader(params.loadSize, offset).fold(
            ifLeft = { LoadResult.Error(Exception(it.toMessage())) },
            ifRight = { (items, hasMore) ->
                LoadResult.Page(
                    data = items,
                    prevKey = if (offset == 0) null else offset - params.loadSize,
                    nextKey = if (!hasMore) null else offset + items.size
                )
            }
        )
    }
}

internal fun SearchError.toMessage(): String = when (this) {
    is SearchError.NetworkError -> "Network error: $message"
    is SearchError.ServerError -> "Server error ($code): $message"
    is SearchError.ClientError -> "Request error ($code): $message"
    is SearchError.Unauthorized -> "Authentication required"
    is SearchError.NotFound -> "Not found"
    is SearchError.Unknown -> "Unknown error: $message"
}
