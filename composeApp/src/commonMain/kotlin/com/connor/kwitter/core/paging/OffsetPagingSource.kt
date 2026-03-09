package com.connor.kwitter.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import arrow.core.raise.context.Raise
import arrow.core.raise.fold

internal class OffsetPagingSource<E, V : Any>(
    private val loader: context(Raise<E>) suspend (limit: Int, offset: Int) -> Pair<List<V>, Boolean>,
    private val mapError: (E) -> Throwable = { Exception(it.toString()) }
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
        return fold(
            block = { loader(params.loadSize, offset) },
            catch = { LoadResult.Error(it) },
            recover = { error -> LoadResult.Error(mapError(error)) },
            transform = { (items, hasMore) ->
                LoadResult.Page(
                    data = items,
                    prevKey = if (offset == 0) null else offset - params.loadSize,
                    nextKey = if (!hasMore) null else offset + items.size
                )
            }
        )
    }
}
