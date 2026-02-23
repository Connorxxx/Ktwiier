package com.connor.kwitter.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import arrow.core.Either

internal class OffsetPagingSource<V : Any>(
    private val loader: suspend (limit: Int, offset: Int) -> Either<Any?, Pair<List<V>, Boolean>>
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
            ifLeft = { LoadResult.Error(Exception(it.toString())) },
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
