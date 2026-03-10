package com.connor.kwitter.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import arrow.core.raise.Raise
import arrow.core.raise.fold

private typealias PageLoader<E, V> = suspend Raise<E>.(limit: Int, offset: Int) -> Pair<List<V>, Boolean>

internal class OffsetPagingSource<E, V : Any>(
    private val mapError: (E) -> Throwable = { Exception(it.toString()) },
    private val loader: PageLoader<E, V>
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
        return fold<E, Pair<List<V>, Boolean>, LoadResult<Int, V>>(
            block = { loader(params.loadSize, offset) },
            catch = { throwable -> LoadResult.Error(throwable) },
            recover = { error -> LoadResult.Error(mapError(error)) },
            transform = { result ->
                val (items, hasMore) = result
                LoadResult.Page(
                    data = items,
                    prevKey = if (offset == 0) null else offset - params.loadSize,
                    nextKey = if (!hasMore) null else offset + items.size
                )
            }
        )
    }
}
