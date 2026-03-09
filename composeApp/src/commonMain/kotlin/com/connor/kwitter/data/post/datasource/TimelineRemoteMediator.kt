package com.connor.kwitter.data.post.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import arrow.core.raise.fold
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.data.post.local.PostEntity
import com.connor.kwitter.data.post.local.toEntity
import com.connor.kwitter.domain.post.model.PostPageQuery

private const val TIMELINE_LABEL = "timeline"
private const val PAGE_SIZE = 20

@OptIn(ExperimentalPagingApi::class)
class TimelineRemoteMediator(
    private val remoteDataSource: PostRemoteDataSource,
    private val database: AppDatabase
) : RemoteMediator<Int, PostEntity>() {

    private val postDao = database.postDao()

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        val cursor: Long? = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = postDao.getRemoteKeyByLabel(TIMELINE_LABEL)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                remoteKey.nextCursor ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return fold(
            block = {
                remoteDataSource.getTimeline(
                    query = PostPageQuery(limit = PAGE_SIZE, beforeId = cursor)
                )
            },
            catch = { MediatorResult.Error(it) },
            recover = { error ->
                MediatorResult.Error(Exception(error.toString()))
            },
            transform = { postList ->
                val entities = postList.posts.mapIndexed { index, post ->
                    post.toEntity(timelineIndex = index)
                }

                val endReached = postList.posts.isEmpty() || !postList.hasMore
                val nextCursor = if (endReached) null else postList.nextCursor

                when (loadType) {
                    LoadType.REFRESH -> postDao.replaceTimeline(
                        label = TIMELINE_LABEL,
                        posts = entities,
                        nextCursor = nextCursor
                    )
                    LoadType.APPEND -> postDao.appendTimeline(
                        label = TIMELINE_LABEL,
                        posts = entities,
                        nextCursor = nextCursor
                    )
                    LoadType.PREPEND -> Unit
                }

                MediatorResult.Success(endOfPaginationReached = endReached)
            }
        )
    }
}
